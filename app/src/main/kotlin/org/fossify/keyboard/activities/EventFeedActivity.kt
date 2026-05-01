package org.fossify.keyboard.activities

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.keyboard.R
import org.fossify.keyboard.databinding.ActivityEventFeedBinding
import org.fossify.keyboard.databinding.ItemSensorReadingBinding
import org.fossify.keyboard.databinding.ItemTimingEventBinding
import org.fossify.keyboard.helpers.LiveCaptureSessionStore
import org.fossify.keyboard.models.KeyTimingEvent
import org.fossify.keyboard.models.SensorReadingEvent

class EventFeedActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityEventFeedBinding::inflate)

    private lateinit var timingAdapter: TimingAdapter
    private lateinit var sensorAdapter: SensorAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        timingAdapter = TimingAdapter()
        binding.eventFeedTimingList.apply {
            layoutManager = LinearLayoutManager(this@EventFeedActivity)
            adapter = timingAdapter
        }

        sensorAdapter = SensorAdapter()
        binding.eventFeedSensorList.apply {
            layoutManager = LinearLayoutManager(this@EventFeedActivity)
            adapter = sensorAdapter
        }

        binding.eventFeedToolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.event_feed_refresh) { loadData(); true } else false
        }

        setupEdgeToEdge(padBottomSystem = listOf(binding.eventFeedNestedScrollview))
        setupMaterialScrollListener(binding.eventFeedNestedScrollview, binding.eventFeedAppbar)
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.eventFeedAppbar, NavigationIcon.Arrow)
        loadData()
    }

    private fun loadData() {
        val timingEvents = LiveCaptureSessionStore.getTimingEvents().asReversed()
        val sensorReadings = LiveCaptureSessionStore.getSensorReadings().asReversed()

        val hasAnyData = timingEvents.isNotEmpty() || sensorReadings.isNotEmpty()

        if (hasAnyData) {
            binding.eventFeedEmptyText.beGone()
            binding.eventFeedTimingLabel.beVisible()
            binding.eventFeedTimingHeader.beVisible()
            binding.eventFeedSensorLabel.beVisible()
            binding.eventFeedSensorHeader.beVisible()
        } else {
            binding.eventFeedEmptyText.beVisible()
            binding.eventFeedTimingLabel.beGone()
            binding.eventFeedTimingHeader.beGone()
            binding.eventFeedSensorLabel.beGone()
            binding.eventFeedSensorHeader.beGone()
        }

        binding.eventFeedToolbar.title = if (timingEvents.isNotEmpty()) {
            "${getString(R.string.event_feed_title)} (${timingEvents.size})"
        } else {
            getString(R.string.event_feed_title)
        }

        timingAdapter.setEvents(timingEvents)
        sensorAdapter.setReadings(sensorReadings)
    }

    private inner class TimingAdapter : RecyclerView.Adapter<TimingAdapter.VH>() {
        private val events = mutableListOf<KeyTimingEvent>()

        fun setEvents(newEvents: List<KeyTimingEvent>) {
            events.clear()
            events.addAll(newEvents)
            notifyDataSetChanged()
        }

        inner class VH(private val b: ItemTimingEventBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(event: KeyTimingEvent) {
                val ms = { v: Long -> if (v >= 0L) "${v}ms" else "—" }
                b.eventCategory.text = event.eventCategory
                b.eventIkd.text = ms(event.ikdMs)
                b.eventHold.text = ms(event.holdTimeMs)
                b.eventFlight.text = ms(event.flightTimeMs)
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(ItemTimingEventBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(events[position])
        override fun getItemCount() = events.size
    }

    private inner class SensorAdapter : RecyclerView.Adapter<SensorAdapter.VH>() {
        private val readings = mutableListOf<SensorReadingEvent>()

        fun setReadings(newReadings: List<SensorReadingEvent>) {
            readings.clear()
            readings.addAll(newReadings)
            notifyDataSetChanged()
        }

        inner class VH(private val b: ItemSensorReadingBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(reading: SensorReadingEvent) {
                b.sensorType.text = reading.sensorType
                b.sensorX.text = "%.3f".format(reading.x)
                b.sensorY.text = "%.3f".format(reading.y)
                b.sensorZ.text = "%.3f".format(reading.z)
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(ItemSensorReadingBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(readings[position])
        override fun getItemCount() = readings.size
    }
}
