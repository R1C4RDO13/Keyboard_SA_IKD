package org.fossify.keyboard.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.keyboard.R
import org.fossify.keyboard.adapters.SessionsAdapter
import org.fossify.keyboard.databinding.ActivitySessionsListBinding
import org.fossify.keyboard.extensions.ikdDB
import org.fossify.keyboard.helpers.IkdCsvWriter
import org.fossify.keyboard.helpers.IkdCsvWriter.asSensorRow
import org.fossify.keyboard.helpers.IkdCsvWriter.asTimingRow
import org.fossify.keyboard.models.SessionRecord
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SessionsListActivity : SimpleActivity() {
    private val binding by viewBinding(ActivitySessionsListBinding::inflate)

    private val adapter = SessionsAdapter(
        sessions = emptyList(),
        onClick = ::onSessionClicked,
        onLongClick = ::onSessionLongPressed,
    )

    private var pendingExportSessionId: String? = null

    private val saveSessionCsvLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        val sessionId = pendingExportSessionId
        pendingExportSessionId = null
        if (uri == null || sessionId == null) return@registerForActivityResult
        exportSessionToUri(sessionId, uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.sessionsListRecycler.layoutManager = LinearLayoutManager(this)
        binding.sessionsListRecycler.adapter = adapter

        binding.sessionsListToolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.sessions_export_all, R.id.sessions_delete_all -> {
                    toast(R.string.coming_soon)
                    true
                }
                else -> false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.sessionsListAppbar, NavigationIcon.Arrow)
        loadSessions()
    }

    private fun loadSessions() {
        ensureBackgroundThread {
            val sessions = ikdDB.SessionDao().getAllSessions()
            runOnUiThread {
                adapter.setSessions(sessions)
                binding.sessionsListEmpty.beVisibleIf(sessions.isEmpty())
                binding.sessionsListRecycler.beVisibleIf(sessions.isNotEmpty())
            }
        }
    }

    private fun onSessionClicked(session: SessionRecord) {
        Intent(this, EventFeedActivity::class.java).apply {
            putExtra(EventFeedActivity.EXTRA_SESSION_ID, session.sessionId)
            startActivity(this)
        }
    }

    private fun onSessionLongPressed(session: SessionRecord) {
        val actions = arrayOf(
            getString(R.string.session_action_export),
            getString(R.string.session_action_delete),
        )
        AlertDialog.Builder(this)
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> startSessionExport(session)
                    1 -> confirmDeleteSession(session)
                }
            }
            .show()
    }

    private fun startSessionExport(session: SessionRecord) {
        pendingExportSessionId = session.sessionId
        val shortId = session.sessionId.take(SHORT_ID_LENGTH)
        val datePart = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        saveSessionCsvLauncher.launch("ikd_session_${shortId}_${datePart}.csv")
    }

    private fun exportSessionToUri(sessionId: String, uri: android.net.Uri) {
        ensureBackgroundThread {
            try {
                val events = ikdDB.IkdEventDao().getEventsForSession(sessionId)
                val samples = ikdDB.SensorSampleDao().getSamplesForSession(sessionId)
                contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.bufferedWriter().use { writer ->
                        IkdCsvWriter.writeSessionCsv(
                            writer,
                            events.map { it.asTimingRow() },
                            samples.map { it.asSensorRow() },
                        )
                    }
                }
                runOnUiThread { toast(R.string.ikd_export_session_success) }
            } catch (e: IOException) {
                e.printStackTrace()
                runOnUiThread { toast(R.string.ikd_export_session_error) }
            } catch (e: SecurityException) {
                e.printStackTrace()
                runOnUiThread { toast(R.string.ikd_export_session_error) }
            }
        }
    }

    private fun confirmDeleteSession(session: SessionRecord) {
        AlertDialog.Builder(this)
            .setTitle(R.string.ikd_delete_session_confirm_title)
            .setMessage(R.string.ikd_delete_session_confirm_message)
            .setPositiveButton(R.string.yes) { _, _ -> performDeleteSession(session) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun performDeleteSession(session: SessionRecord) {
        ensureBackgroundThread {
            ikdDB.SessionDao().deleteSession(session.sessionId)
            runOnUiThread {
                toast(R.string.ikd_delete_session_success)
                loadSessions()
            }
        }
    }

    companion object {
        private const val SHORT_ID_LENGTH = 8
    }
}
