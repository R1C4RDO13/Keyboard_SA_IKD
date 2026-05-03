package org.fossify.keyboard.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.keyboard.R
import org.fossify.keyboard.databinding.ItemSessionSummaryBinding
import org.fossify.keyboard.helpers.IkdFormatters
import org.fossify.keyboard.models.SessionRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SessionsAdapter(
    private var sessions: List<SessionRecord>,
    private val onClick: (SessionRecord) -> Unit,
    private val onLongClick: (SessionRecord) -> Unit = {}
) : RecyclerView.Adapter<SessionsAdapter.ViewHolder>() {

    companion object {
        private const val MS_PER_MINUTE = 60_000L
        private const val DATE_PATTERN = "MMM d yyyy, HH:mm"
    }

    private val dateFormatter = SimpleDateFormat(DATE_PATTERN, Locale.getDefault())

    fun setSessions(newSessions: List<SessionRecord>) {
        sessions = newSessions
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSessionSummaryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(sessions[position])
    }

    override fun getItemCount(): Int = sessions.size

    inner class ViewHolder(
        private val binding: ItemSessionSummaryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(session: SessionRecord) {
            val context: Context = binding.root.context
            val textColor = context.getProperTextColor()

            binding.itemSessionStartedAt.setTextColor(textColor)
            binding.itemSessionDuration.setTextColor(textColor)
            binding.itemSessionStats.setTextColor(textColor)
            binding.itemSessionKpm.setTextColor(textColor)

            binding.itemSessionStartedAt.text = dateFormatter.format(Date(session.startedAt))

            val durationMs = session.endedAt?.let { it - session.startedAt } ?: 0L
            binding.itemSessionDuration.text = context.getString(
                R.string.session_summary_duration, IkdFormatters.formatDuration(durationMs)
            )

            binding.itemSessionStats.text = context.getString(
                R.string.session_summary_stats, session.eventCount, session.sensorCount
            )

            val showKpm = session.endedAt != null && durationMs > 0 && session.eventCount > 0
            binding.itemSessionKpm.beVisibleIf(showKpm)
            if (showKpm) {
                val kpm = (session.eventCount * MS_PER_MINUTE / durationMs).toInt()
                binding.itemSessionKpm.text = context.getString(R.string.session_summary_kpm, kpm)
            }

            binding.root.setOnClickListener { onClick(session) }
            binding.root.setOnLongClickListener {
                onLongClick(session)
                true
            }
        }
    }
}
