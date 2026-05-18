package com.archivesentinel.service

import com.archivesentinel.domain.MediaFile
import com.archivesentinel.domain.OptimizationRun
import com.archivesentinel.domain.OptimizationRunItem
import com.archivesentinel.domain.OptimizationRunItemRepository
import com.archivesentinel.domain.OptimizationRunRepository
import com.archivesentinel.domain.PrecheckRun
import com.archivesentinel.domain.PrecheckRunRepository
import com.archivesentinel.domain.StorageRoot
import jakarta.mail.internet.MimeMessage
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.JavaMailSenderImpl
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.web.util.HtmlUtils
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Properties
import java.util.UUID
import kotlin.io.path.createDirectories
import kotlin.io.path.name

data class ReportArtifact(val body: String, val contentType: String, val filename: String)

@Service
class ReportService(
    private val settingsService: SettingsService,
    private val monitoringService: MonitoringService,
    private val mailSender: JavaMailSender,
    private val optimizationRunRepository: OptimizationRunRepository,
    private val optimizationRunItemRepository: OptimizationRunItemRepository,
    private val precheckRunRepository: PrecheckRunRepository,
    private val appPathService: AppPathService,
    @Value("\${app.reports.root}") private val reportsRoot: String,
) {
    private var lastSentAt: Instant? = null

    fun artifactUrl(kind: String, id: UUID): String = "/api/reports/artifacts/$kind/$id"

    fun artifact(kind: String, id: UUID): ReportArtifact {
        val path = when (kind) {
            "run" -> optimizationRunRepository.findById(id).orElseThrow().reportPath
            "run-log" -> optimizationRunRepository.findById(id).orElseThrow().logPath
            "precheck" -> precheckRunRepository.findById(id).orElseThrow().reportPath
            "precheck-log" -> precheckRunRepository.findById(id).orElseThrow().logPath
            else -> null
        }?.let { Paths.get(it) } ?: error("Report artifact not found")
        return ReportArtifact(
            body = Files.readString(path),
            contentType = if (path.toString().endsWith(".html")) "text/html; charset=UTF-8" else "text/plain; charset=UTF-8",
            filename = path.name,
        )
    }

    fun generatePrecheckReport(precheck: PrecheckRun, roots: List<StorageRoot>, files: List<MediaFile>): Path {
        val report = reportPath("precheck", precheck.id, "html")
        val ratio = settingsService.current().estimatedOutputRatioPercent / 100.0
        val rows = files.map {
            val expectedBytes = (it.sizeBytes * ratio).toLong()
            ReportFileRow(
                storageRoot = roots.firstOrNull { root -> root.id == it.storageRootId }?.label ?: "Selected files",
                storageRootPath = roots.firstOrNull { root -> root.id == it.storageRootId }?.path,
                optimizedRoot = null,
                archiveRoot = null,
                displayPath = it.relativePath,
                originalPath = it.originalPath,
                optimizedPath = null,
                oldBytes = it.sizeBytes,
                newBytes = expectedBytes,
                savedBytes = (it.sizeBytes - expectedBytes).coerceAtLeast(0),
                status = it.status.name,
                failure = if (it.status.name == "FAILED") it.validationJson else null,
            )
        }
        val body = summaryCards(
            "Started" to timestamp(precheck.startedAt),
            "Completed" to timestamp(precheck.completedAt),
            "Status" to precheck.status.name,
            "Files scanned" to precheck.totalFiles.toString(),
            "Current size" to bytes(precheck.totalBytes),
            "Estimated output" to bytes(precheck.estimatedOutputBytes),
            "Estimated savings" to bytes(precheck.estimatedSavingsBytes),
        ) + failureSummary(rows) + """
            <section class="band">
              <h2>Selected roots</h2>
              <div class="pill-row">${roots.joinToString("") { "<span>${esc(it.label)} - ${esc(it.path)}</span>" }}</div>
            </section>
            <section class="band">
              <h2>Inventory tree</h2>
              <p class="muted">All scanned files are listed, including unselected and failed items. Folders are collapsed by default so million-file inventories stay navigable.</p>
              ${reportSearch()}
              ${fileTree(rows, pendingWhenZero = false)}
            </section>
        """.trimIndent()
        Files.writeString(report, shell("Precheck report", "Archive Sentinel", body))
        return report
    }

    fun generatePrecheckLog(precheck: PrecheckRun, roots: List<StorageRoot>, files: List<MediaFile>): Path {
        val log = reportPath("precheck", precheck.id, "log")
        val text = buildString {
            appendLine("Archive Sentinel precheck ${precheck.id}")
            appendLine("Started: ${precheck.startedAt}")
            appendLine("Completed: ${precheck.completedAt}")
            appendLine("Input size: ${bytes(precheck.totalBytes)}")
            appendLine("Roots:")
            roots.forEach { appendLine("- ${it.label}: ${it.path}") }
            appendLine()
            files.forEach {
                appendLine(
                    listOf(
                        it.status,
                        it.sizeBytes,
                        it.originalPath,
                        failureMessage(it).orEmpty(),
                    ).joinToString("\t"),
                )
            }
        }
        Files.writeString(log, text)
        return log
    }

    fun generatePrecheckFailureReport(precheck: PrecheckRun): Path {
        val report = reportPath("precheck", precheck.id, "html")
        Files.writeString(
            report,
            shell(
                "Precheck failed",
                "Archive Sentinel",
                summaryCards(
                    "Started" to timestamp(precheck.startedAt),
                    "Completed" to timestamp(precheck.completedAt),
                    "Status" to precheck.status.name,
                ) + """<section class="band danger"><h2>Failure reason</h2><p>${esc(precheck.errorMessage)}</p></section>""",
            ),
        )
        return report
    }

    fun generatePrecheckFailureLog(precheck: PrecheckRun): Path {
        val log = reportPath("precheck", precheck.id, "log")
        Files.writeString(log, "Precheck ${precheck.id} failed: ${precheck.errorMessage.orEmpty()}")
        return log
    }

    fun generateRunReport(run: OptimizationRun): Path {
        val report = reportPath("run", run.id, "html")
        val items = optimizationRunItemRepository.findByRunId(run.id)
        val rows = items.map {
            ReportFileRow(
                storageRoot = it.storageRootLabel ?: "Unknown root",
                storageRootPath = it.storageRootPath,
                optimizedRoot = it.optimizedRoot,
                archiveRoot = it.archiveRoot,
                displayPath = relativeDisplayPath(it),
                originalPath = it.archivedPath ?: it.originalPath,
                optimizedPath = it.optimizedPath,
                oldBytes = it.inputBytes,
                newBytes = it.outputBytes,
                savedBytes = it.savedBytes,
                status = it.status.name,
                failure = it.errorMessage ?: it.validationJson?.takeIf { _ -> it.status.name == "FAILED" },
            )
        }
        val body = summaryCards(
            "Started" to timestamp(run.startedAt),
            "Completed" to timestamp(run.completedAt),
            "Run status" to run.status.name,
            "Progress" to "${run.completedFiles}/${run.totalFiles}",
            "Input" to bytes(run.totalInputBytes),
            "Output" to bytes(run.totalOutputBytes),
            "Saved" to bytes(rows.sumOf { it.savedBytes }),
            "Failures" to run.failedFiles.toString(),
        ) + failureSummary(rows) + """
            <section class="band">
              <h2>Run file tree</h2>
              <p class="muted">Grouped by storage root. Open a root, then folders underneath it, to inspect archive links, optimized outputs, savings, and failures.</p>
              ${reportSearch()}
              ${rootTree(rows)}
            </section>
        """.trimIndent()
        Files.writeString(report, shell("Optimization run ${run.id}", "Archive Sentinel", body))
        return report
    }

    fun generateRunLog(run: OptimizationRun): Path {
        val log = reportPath("run", run.id, "log")
        val items = optimizationRunItemRepository.findByRunId(run.id)
        val text = buildString {
            appendLine("Archive Sentinel run ${run.id}")
            appendLine("Status: ${run.status}")
            appendLine("Started: ${run.startedAt}")
            appendLine("Completed: ${run.completedAt}")
            appendLine("Input: ${gb(run.totalInputBytes)} (${run.totalInputBytes} bytes)")
            appendLine("Output: ${gb(run.totalOutputBytes)} (${run.totalOutputBytes} bytes)")
            appendLine("Tdarr submission batch size: ${settingsService.current().tdarrSubmissionConcurrency}")
            appendLine()
            items.forEach {
                appendLine(
                    listOf(
                        it.status,
                        gb(it.inputBytes),
                        gb(it.outputBytes),
                        gb(it.savedBytes),
                        it.storageRootLabel.orEmpty(),
                        it.optimizedRoot.orEmpty(),
                        it.archiveRoot.orEmpty(),
                        it.originalPath,
                        it.optimizedPath.orEmpty(),
                        it.errorMessage.orEmpty(),
                    ).joinToString("\t"),
                )
            }
        }
        Files.writeString(log, text)
        return log
    }

    @Scheduled(fixedDelay = 3_600_000)
    fun sendIfDue() {
        val settings = settingsService.current()
        if (settings.reportRecipients.isBlank()) return
        val lastSent = lastSentAt
        if (lastSent != null && lastSent.plus(settings.reportIntervalHours.toLong(), ChronoUnit.HOURS).isAfter(Instant.now())) return
        sendNow(settings.reportRecipients.split(",").map { it.trim() }.filter { it.isNotBlank() })
        lastSentAt = Instant.now()
    }

    fun html(): String {
        val summary = monitoringService.summary()
        val body = summaryCards(
            "Tracked files" to summary.totalFiles.toString(),
            "Archived originals" to summary.archivedFiles.toString(),
            "Failed files" to summary.failedFiles.toString(),
            "Completed runs" to summary.completedRuns.toString(),
            "Pending approvals" to summary.pendingDeletionApprovals.toString(),
            "Historical savings" to bytes(summary.historicalSavingsBytes),
        )
        return shell("Aggregate report", "Archive Sentinel", body)
    }

    fun sendNow(recipients: List<String>) {
        if (recipients.isEmpty()) return
        val settings = settingsService.current()
        val sender = senderForSettings()
        val message: MimeMessage = sender.createMimeMessage()
        val helper = MimeMessageHelper(message, true)
        helper.setTo(recipients.toTypedArray())
        helper.setSubject("Archive Sentinel report")
        settings.smtpFrom.takeIf { it.isNotBlank() }?.let { helper.setFrom(it) }
        helper.setText(html(), true)
        sender.send(message)
    }

    private fun senderForSettings(): JavaMailSender {
        val settings = settingsService.current()
        if (settings.smtpHost.isBlank()) return mailSender
        return JavaMailSenderImpl().apply {
            host = settings.smtpHost
            port = settings.smtpPort
            username = settings.smtpUsername.takeIf { it.isNotBlank() }
            password = settings.smtpPassword.takeIf { it.isNotBlank() }
            javaMailProperties = Properties().apply {
                put("mail.smtp.auth", settings.smtpAuth.toString())
                put("mail.smtp.starttls.enable", settings.smtpStartTls.toString())
            }
        }
    }

    private fun reportPath(kind: String, id: UUID, extension: String): Path {
        val directory = appPathService.resolve(reportsRoot).resolve(kind)
        directory.createDirectories()
        return directory.resolve("$id.$extension")
    }

    private fun fileTree(rows: List<ReportFileRow>, pendingWhenZero: Boolean = true): String {
        if (rows.isEmpty()) return """<p class="empty">No files matched this report.</p>"""
        return rows.groupBy { parentFolder(it.displayPath) }.toSortedMap().map { (folder, folderRows) ->
            """
            <details>
              <summary><strong>${esc(folder)}</strong><span>${folderRows.size} files</span></summary>
              <table class="sortable">
                <thead><tr><th>File</th><th>Original/archive</th><th>Optimized</th><th>Old size</th><th>New size</th><th>Saved</th><th>Status</th></tr></thead>
                <tbody>
                  ${folderRows.joinToString("") { row ->
                """
                    <tr data-search="${esc(listOf(row.displayPath, row.originalPath, row.optimizedPath, row.status, row.storageRoot).joinToString(" "))}">
                      <td>${esc(fileName(row.displayPath))}${row.failure?.let { "<small>${esc(it)}</small>" } ?: ""}</td>
                      <td>${fileLink(row.originalPath)}</td>
                      <td>${fileLink(row.optimizedPath)}</td>
                      <td>${bytes(row.oldBytes)}</td>
                      <td>${if (!pendingWhenZero || row.newBytes > 0) bytes(row.newBytes) else "Pending"}</td>
                      <td>${bytes(row.savedBytes)}</td>
                      <td><span class="status">${esc(row.status)}</span></td>
                    </tr>
                """.trimIndent()
            }}
                </tbody>
              </table>
            </details>
            """.trimIndent()
        }.joinToString("\n")
    }

    private fun rootTree(rows: List<ReportFileRow>): String {
        if (rows.isEmpty()) return """<p class="empty">No files matched this report.</p>"""
        return rows.groupBy { it.storageRoot }.toSortedMap().map { (root, rootRows) ->
            val first = rootRows.first()
            """
            <details class="root-detail" open>
              <summary><strong>${esc(root)}</strong><span>${rootRows.size} files</span></summary>
              <div class="root-targets">
                ${first.storageRootPath?.let { "<span>Source ${esc(it)}</span>" } ?: ""}
                ${first.optimizedRoot?.let { "<span>Optimized ${esc(it)}</span>" } ?: ""}
                ${first.archiveRoot?.let { "<span>Archive ${esc(it)}</span>" } ?: ""}
              </div>
              ${fileTree(rootRows)}
            </details>
            """.trimIndent()
        }.joinToString("\n")
    }

    private fun reportSearch(): String =
        """<div class="report-tools"><input id="reportSearch" placeholder="Search filenames, roots, paths, or statuses" /></div>"""

    private fun failureSummary(rows: List<ReportFileRow>): String {
        val failures = rows.filter { !it.failure.isNullOrBlank() }
        if (failures.isEmpty()) return ""
        return """
            <section class="band danger">
              <h2>Failure reasons</h2>
              <ul>${failures.joinToString("") { "<li><strong>${esc(it.displayPath)}</strong><small>${esc(it.failure)}</small></li>" }}</ul>
            </section>
        """.trimIndent()
    }

    private fun summaryCards(vararg cards: Pair<String, String>): String =
        """<section class="cards">${cards.joinToString("") { "<article><span>${esc(it.first)}</span><strong>${esc(it.second)}</strong></article>" }}</section>"""

    private fun shell(title: String, product: String, body: String): String =
        """
        <!doctype html>
        <html>
          <head>
            <meta charset="utf-8" />
            <meta name="viewport" content="width=device-width, initial-scale=1" />
            <title>${esc(title)}</title>
            <style>
              :root { color: #17202a; background: #eef3f7; font-family: Inter, Segoe UI, Arial, sans-serif; }
              body { margin: 0; background: linear-gradient(135deg, #eef7f5, #f5f0ea 45%, #eef3f7); }
              header { padding: 32px clamp(18px, 4vw, 48px); color: white; background: linear-gradient(120deg, #16324f, #126c66 58%, #c75f3a); }
              header p, header h1 { margin: 0; }
              header p { opacity: .82; font-size: 14px; text-transform: uppercase; letter-spacing: .08em; }
              header h1 { margin-top: 8px; font-size: clamp(28px, 4vw, 48px); letter-spacing: 0; }
              main { padding: 22px clamp(14px, 3vw, 38px) 38px; display: grid; gap: 18px; }
              .cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 12px; }
              article, .band, details { background: rgba(255,255,255,.94); border: 1px solid rgba(34,52,69,.12); border-radius: 8px; box-shadow: 0 14px 40px rgba(22,50,79,.08); }
              article { padding: 16px; display: grid; gap: 8px; min-width: 0; }
              article span { color: #596675; font-size: 13px; }
              article strong { font-size: clamp(17px, 2.2vw, 24px); line-height: 1.25; overflow-wrap: anywhere; word-break: break-word; }
              .band { padding: 18px; }
              .band h2 { margin: 0 0 8px; }
              .muted { color: #66717e; margin-top: 0; }
              .danger { border-color: #d78365; background: #fff8f4; }
              .pill-row { display: flex; flex-wrap: wrap; gap: 8px; }
              .pill-row span, .status { display: inline-flex; border-radius: 999px; padding: 4px 9px; background: #e9f4f2; color: #126c66; font-size: 12px; }
              .report-tools { margin: 12px 0; }
              .report-tools input { width: min(560px, 100%); box-sizing: border-box; border: 1px solid #cfd8df; border-radius: 6px; padding: 10px 12px; font: inherit; }
              .root-targets { display: flex; flex-wrap: wrap; gap: 8px; padding: 0 16px 12px; }
              .root-targets span { border: 1px solid #dde6eb; border-radius: 6px; padding: 6px 8px; background: #f8fafb; color: #415061; font-size: 12px; }
              details { margin-top: 10px; overflow: hidden; }
              summary { cursor: pointer; display: flex; justify-content: space-between; gap: 12px; padding: 14px 16px; }
              table { width: 100%; border-collapse: collapse; font-size: 13px; }
              th, td { padding: 10px 12px; border-top: 1px solid #edf1f4; text-align: left; vertical-align: top; }
              th { color: #5b6673; background: #f8fafb; font-weight: 700; cursor: pointer; }
              td small { display: block; margin-top: 4px; color: #a4422d; max-width: 70ch; }
              li { margin: 8px 0; }
              li small { display: block; margin-top: 3px; color: #a4422d; overflow-wrap: anywhere; }
              a { color: #145c9e; text-decoration: none; overflow-wrap: anywhere; }
              a:hover { text-decoration: underline; }
              .empty { margin: 0; color: #66717e; }
            </style>
          </head>
          <body>
            <header><p>${esc(product)}</p><h1>${esc(title)}</h1></header>
            <main>$body</main>
            <iframe name="reveal-target" hidden></iframe>
            <script>
              const search = document.getElementById('reportSearch');
              if (search) {
                search.addEventListener('input', () => {
                  const q = search.value.toLowerCase();
                  document.querySelectorAll('tbody tr').forEach((row) => {
                    row.style.display = row.dataset.search.toLowerCase().includes(q) ? '' : 'none';
                  });
                });
              }
              document.querySelectorAll('table.sortable th').forEach((header, index) => {
                header.addEventListener('click', () => {
                  const table = header.closest('table');
                  const body = table.querySelector('tbody');
                  const rows = Array.from(body.querySelectorAll('tr'));
                  const asc = header.dataset.sort !== 'asc';
                  rows.sort((a, b) => {
                    const av = a.children[index].innerText.toLowerCase();
                    const bv = b.children[index].innerText.toLowerCase();
                    return asc ? av.localeCompare(bv) : bv.localeCompare(av);
                  });
                  header.dataset.sort = asc ? 'asc' : 'desc';
                  rows.forEach((row) => body.appendChild(row));
                });
              });
            </script>
          </body>
        </html>
        """.trimIndent()

    private fun fileLink(path: String?): String =
        path?.takeIf { it.isNotBlank() }?.let {
            val encoded = java.net.URLEncoder.encode(it, Charsets.UTF_8)
            """<a target="reveal-target" href="/api/paths/reveal?path=$encoded">${esc(it)}</a>"""
        } ?: "n/a"

    private fun parentFolder(path: String): String =
        path.replace("\\", "/").substringBeforeLast("/", "Root")

    private fun fileName(path: String): String =
        path.replace("\\", "/").substringAfterLast("/")

    private fun failureMessage(file: MediaFile): String? =
        if (file.status.name == "FAILED") file.validationJson else null

    private fun relativeDisplayPath(item: OptimizationRunItem): String {
        val root = item.storageRootPath?.takeIf { it.isNotBlank() } ?: return item.originalPath
        return try {
            Paths.get(root).normalize().relativize(Paths.get(item.originalPath).normalize()).toString()
        } catch (_: Exception) {
            item.originalPath
        }
    }

    private fun bytes(value: Long): String {
        val abs = value.coerceAtLeast(0)
        val gb = abs / 1024.0 / 1024.0 / 1024.0
        val mb = abs / 1024.0 / 1024.0
        return if (gb >= 1) "%.2f GB".format(gb) else "%.2f MB".format(mb)
    }

    private fun gb(value: Long): String =
        "%.4f GB".format(value.coerceAtLeast(0) / 1024.0 / 1024.0 / 1024.0)

    private fun timestamp(value: Instant?): String =
        value?.let { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(ZoneId.systemDefault()).format(it) } ?: "Pending"

    private fun esc(value: Any?): String = HtmlUtils.htmlEscape(value?.toString().orEmpty())
}

private data class ReportFileRow(
    val storageRoot: String,
    val storageRootPath: String?,
    val optimizedRoot: String?,
    val archiveRoot: String?,
    val displayPath: String,
    val originalPath: String?,
    val optimizedPath: String?,
    val oldBytes: Long,
    val newBytes: Long,
    val savedBytes: Long,
    val status: String,
    val failure: String?,
)
