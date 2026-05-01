package org.fossify.keyboard.activities

import android.content.Intent
import android.os.Bundle
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
import org.fossify.keyboard.models.SessionRecord

class SessionsListActivity : SimpleActivity() {
    private val binding by viewBinding(ActivitySessionsListBinding::inflate)

    private val adapter = SessionsAdapter(emptyList(), ::onSessionClicked)

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
}
