package com.liarstudio.maven_indexer.printer

import com.liarstudio.maven_indexer.indexer.MultipleArtifactIndexer.Progress

class ProgressRenderer {

    fun render(progress: Progress) {
        print("\r${progress.getRenderableMessage()}")
    }

    private fun Progress.getRenderableMessage() =
        when (this) {
            is Progress.Simple -> this.getRenderableMessage()
            is Progress.Staged -> this.getRenderableMessage()
            is Progress.Result -> this.getRenderableMessage()
        }

    private fun Progress.Simple.getRenderableMessage(): String =
        if (total != null) {
            "Progress: $current/$total artifacts..."
        } else {
            "Progress: $current artifacts..."
        }

    private fun Progress.Staged.getRenderableMessage(): String =
        if (total != null) {
            "Stage $current/$total: $stageDescription. ${stageProgress.getRenderableMessage()} "
        } else {
            "Stage $current: $stageDescription. ${stageProgress.getRenderableMessage()} "
        }

    private fun Progress.Result.getRenderableMessage(): String =
        "✅ Done indexing all artifacts!\n Statistics: " +
                "\n* Successfully indexed artifacts: $successCount" +
                "\n* Errors: $errorCount" +
                "\n* Total number of artifacts processed: ${successCount + errorCount}"
}