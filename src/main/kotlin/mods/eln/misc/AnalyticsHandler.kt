package mods.eln.misc

/** Binary/source compatibility for old integrations. This fork collects no usage
 * identifiers and performs no analytics requests, regardless of legacy config. */
object AnalyticsHandler {
    fun submitUpstreamAnalytics() = Unit
    fun submitAgeSeriesAnalytics() = Unit
}
