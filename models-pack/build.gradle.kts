plugins {
    id("com.android.asset-pack")
}

assetPack {
    packName.set("models")

    dynamicDelivery {
        // install-time means the models arrive with the app install.
        //
        // The alternative, on-demand, would mean writing download code, a
        // progress screen, and handling a "models not ready" state at
        // startup. The app has no use until the models are there, so
        // install-time is simpler and better for the user.
        //
        // The cost is that the download is around 143 MB and the app takes
        // around 255 MB on the phone. That is under the 200 MB that makes
        // Play warn the user on mobile data. See docs/DECISIONS.md.
        deliveryType.set("install-time")
    }
}