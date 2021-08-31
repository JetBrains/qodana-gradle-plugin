package org.jetbrains.qodana

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.qodana.tasks.CleanInspectionsTask
import org.jetbrains.qodana.tasks.RunInspectionsTask
import org.jetbrains.qodana.tasks.StopInspectionsTask
import org.jetbrains.qodana.tasks.UpdateInspectionsTask

@Suppress("unused", "UnstableApiUsage")
class QodanaPlugin : Plugin<Project> {

    /**
     * Configure Qodana tasks and extension.
     */
    override fun apply(project: Project) {

        // `qodana {}` Extension
        val extension = project.extensions.create(QodanaPluginConstants.EXTENSION_NAME, QodanaPluginExtension::class.java).also { ext ->
            ext.executable.convention(QodanaPluginConstants.EXECUTABLE)
            ext.projectPath.convention(project.projectDir.canonicalPath)
            ext.resultsPath.convention(project.provider {
                "${ext.projectPath.get()}/build/results"
            })
            ext.saveReport.convention(false)
            ext.showReport.convention(false)
            ext.showReportPort.convention(8080)
            ext.autoUpdate.convention(true)
        }

        // `updateInspections` task
        project.tasks.register(QodanaPluginConstants.UPDATE_INSPECTIONS_TASK_NAME, UpdateInspectionsTask::class.java) { task ->
            task.group = QodanaPluginConstants.GROUP_NAME
            task.description = "Pulls the latest Qodana Inspections Docker container"

            task.dockerImageName.convention(project.provider {
                val runInspectionsTaskProvider = project.tasks.named(QodanaPluginConstants.RUN_INSPECTIONS_TASK_NAME)
                val runInspectionsTask = runInspectionsTaskProvider.get() as RunInspectionsTask
                runInspectionsTask.dockerImageName.get()
            })
            task.dockerExecutable.convention(extension.executable)
        }

        // `runInspections` task
        project.tasks.register(QodanaPluginConstants.RUN_INSPECTIONS_TASK_NAME, RunInspectionsTask::class.java) { task ->
            task.group = QodanaPluginConstants.GROUP_NAME
            task.description = "Starts Qodana Inspections in Docker container"

            task.dockerExecutable.convention(extension.executable)
            task.dockerContainerName.convention(QodanaPluginConstants.DOCKER_CONTAINER_NAME_INSPECTIONS)
            task.dockerImageName.convention(QodanaPluginConstants.DOCKER_IMAGE_NAME_INSPECTIONS)
            task.projectDir.convention(project.provider {
                project.file(extension.projectPath)
            })
            task.resultsDir.convention(project.provider {
                project.file(extension.resultsPath)
            })
            task.cacheDir.convention(project.provider {
                extension.cachePath.orNull?.let {
                    project.file(it)
                }
            })
            task.saveReport.convention(extension.saveReport)
            task.showReport.convention(extension.showReport)
            task.showReportPort.convention(extension.showReportPort)
            task.changes.convention(false)

            task.dockerPortBindings.set(project.provider {
                listOfNotNull(
                    "${task.showReportPort.get()}:8080",
                )
            })
            task.dockerVolumeBindings.set(project.provider {
                listOfNotNull(
                    "${task.projectDir.get().canonicalPath}:/data/project",
                    "${task.resultsDir.get().canonicalPath}:/data/results",
                    task.cacheDir.orNull?.let {
                        "${it.canonicalPath}:/data/cache"
                    },
                    task.profilePath.orNull?.let {
                        "$it:/data/profile.xml"
                    },
                    task.disabledPluginsPath.orNull?.let {
                        "$it:/root/.config/idea/disabled_plugins.txt"
                    },
                )
            })
            task.dockerEnvParameters.set(project.provider {
                listOfNotNull(
                    "--save-report".takeIf { task.saveReport.get() },
                    "--show-report".takeIf { task.showReport.get() },
                    task.jvmParameters.get().takeIf { it.isNotEmpty() }?.let {
                        "IDE_PROPERTIES_PROPERTY=${it.joinToString(" ")}"
                    },
                )
            })
            task.arguments.set(project.provider {
                listOfNotNull(
                    "-changes".takeIf { task.changes.get() }
                )
            })

            val updateInspectionsTaskProvider = project.tasks.named(QodanaPluginConstants.UPDATE_INSPECTIONS_TASK_NAME)
            val updateInspectionsTask = updateInspectionsTaskProvider.get() as UpdateInspectionsTask

            task.dependsOn(updateInspectionsTask)
            updateInspectionsTask.onlyIf { extension.autoUpdate.get() }
        }

        // `stopInspections` task
        project.tasks.register(QodanaPluginConstants.STOP_INSPECTIONS_TASK_NAME, StopInspectionsTask::class.java) { task ->
            task.group = QodanaPluginConstants.GROUP_NAME
            task.description = "Stops Qodana Inspections Docker container"
            task.isIgnoreExitValue = true

            task.dockerContainerName.convention(project.provider {
                val runInspectionsTaskProvider = project.tasks.named(QodanaPluginConstants.RUN_INSPECTIONS_TASK_NAME)
                val runInspectionsTask = runInspectionsTaskProvider.get() as RunInspectionsTask
                runInspectionsTask.dockerContainerName.get()
            })
            task.dockerExecutable.convention(extension.executable)
        }

        // `cleanInspections` task
        project.tasks.register(QodanaPluginConstants.CLEAN_INSPECTIONS_TASK_NAME, CleanInspectionsTask::class.java) { task ->
            task.group = QodanaPluginConstants.GROUP_NAME
            task.description = "Cleans up Qodana Inspections output directory"

            val runInspectionsTaskProvider = project.tasks.named(QodanaPluginConstants.RUN_INSPECTIONS_TASK_NAME)
            val runInspectionsTask = runInspectionsTaskProvider.get() as RunInspectionsTask
            task.resultsDir.convention(runInspectionsTask.resultsDir)
        }
    }
}
