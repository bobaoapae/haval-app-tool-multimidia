package br.com.redesurftank.havalshisuku.projectors

internal object ClusterCardFlowPolicy {
    data class Decision(
            val pushProjectionStateBeforeCard: Boolean,
            val forceProjectionStateBeforeCard: Boolean,
            val updateVirtualClusterVisibility: Boolean,
            val clearAppliedAppConfigCache: Boolean,
            val syncSecondaryDisplayApps: Boolean,
            val syncVisibleCardValues: Boolean
    )

    fun cardCanAffectManagedAppBounds(previousCard: Int, nextCard: Int): Boolean {
        if (previousCard == nextCard) return false
        return previousCard == ClusterCardIds.NATIVE_CARD || nextCard == ClusterCardIds.NATIVE_CARD
    }

    fun isCardBackedMenu(cardId: Int): Boolean {
        return cardId == ClusterCardIds.MAIN_MENU_CARD ||
                cardId == ClusterCardIds.AIRCON_CARD
    }

    fun shouldUseNativeCardPassThrough(
            cardId: Int,
            warningActive: Boolean,
            projectionActive: Boolean
    ): Boolean {
        // Card 0 is a neutral cluster state; hiding the Presentation drops the active theme.
        return false
    }

    fun decideCardChange(
            nextCard: Int,
            projectionActive: Boolean,
            projectionStateMayBeStale: Boolean,
            hasManagedSecondaryDisplayWork: Boolean,
            cardCanAffectManagedAppBounds: Boolean
    ): Decision {
        val projectionWork = projectionActive || projectionStateMayBeStale
        val managedBoundsWork = hasManagedSecondaryDisplayWork && cardCanAffectManagedAppBounds

        return Decision(
                pushProjectionStateBeforeCard = projectionActive || projectionStateMayBeStale,
                forceProjectionStateBeforeCard = projectionActive,
                updateVirtualClusterVisibility = projectionWork || managedBoundsWork,
                clearAppliedAppConfigCache = managedBoundsWork,
                syncSecondaryDisplayApps = managedBoundsWork,
                syncVisibleCardValues = isCardBackedMenu(nextCard)
        )
    }
}
