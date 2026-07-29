package com.secondmonday.hodith.widget

/** No-op test double — same style as [com.secondmonday.hodith.domain.FakeClock]. Tracks call
 * count so tests can assert a refresh happened without needing a real Context or Glance. */
class FakeWidgetRefresher : WidgetRefresher {
    var refreshCount = 0
        private set

    override suspend fun refreshListWidget() {
        refreshCount++
    }
}
