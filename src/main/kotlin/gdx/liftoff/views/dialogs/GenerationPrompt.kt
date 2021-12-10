package gdx.liftoff.views.dialogs

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Window
import com.badlogic.gdx.utils.ObjectSet
import com.github.czyzby.autumn.annotation.Inject
import com.github.czyzby.autumn.mvc.component.i18n.LocaleService
import com.github.czyzby.autumn.mvc.component.ui.controller.ViewDialogShower
import com.github.czyzby.autumn.mvc.stereotype.ViewDialog
import com.github.czyzby.lml.annotation.LmlActor
import gdx.liftoff.config.inject
import gdx.liftoff.config.threadPool
import gdx.liftoff.data.project.ProjectLogger
import gdx.liftoff.views.MainView
import gdx.liftoff.views.widgets.ScrollableTextArea
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Displayed after generation request was sent.
 */
@ViewDialog(id = "generation", value = "templates/dialogs/generation.lml", cacheInstance = false)
@Suppress("unused") // Referenced via reflection.
class GenerationPrompt : ViewDialogShower, ProjectLogger {
    @Inject private val locale: LocaleService = inject()
    @Inject private val mainView: MainView = inject()

    @LmlActor("close", "exit") private val buttons: ObjectSet<Button> = inject()
    @LmlActor("console") private val console: ScrollableTextArea = inject()
    @LmlActor("scroll") private val scrollPane: ScrollPane = inject()

    private val loggingBuffer = ConcurrentLinkedQueue<String>()

    override fun doBeforeShow(dialog: Window) {
        dialog.invalidate()
        threadPool.execute {
            try {
                logNls("copyStart")
                val project = mainView.createProject()
                logNls("generationStart")
                project.generate()
                logNls("copyEnd")
                mainView.revalidateForm()
                project.includeGradleWrapper(this)
                logNls("generationEnd")
            } catch (exception: Exception) {
                log(exception.javaClass.name + ": " + exception.message)
                exception.stackTrace.forEach { log("  at $it") }
                exception.printStackTrace()
                logNls("generationFail")
            } finally {
                buttons.forEach { it.isDisabled = false }
            }
        }
    }

    override fun logNls(bundleLine: String) = log(locale.i18nBundle.get(bundleLine))
    override fun log(message: String) {
        loggingBuffer.offer(message)
        Gdx.app.postRunnable {
            while (loggingBuffer.isNotEmpty()) {
                if (console.text.isNotBlank()) console.text += '\n'
                console.text += loggingBuffer.poll()
            }
            Gdx.app.postRunnable {
                console.invalidateHierarchy()
                scrollPane.layout()
                scrollPane.scrollPercentY = 1f
            }
        }
    }
}