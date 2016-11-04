package kazari

import fr.hmil.roshttp.response.SimpleHttpResponse
import kazari.codemirror.{CodeMirrorCharCords, PositionBuilder}
import kazari.domhelper.DOMHelper

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import org.scalajs.dom._

import scala.scalajs.js.JSApp
import scala.scalajs.js.annotation.{JSExport, ScalaJSDefined}
import org.denigma.codemirror.{CodeMirror, Editor, EditorConfiguration, PositionLike}
import org.denigma.codemirror.extensions.EditorConfig
import org.scalaexercises.evaluator.{Dependency, EvalResponse, EvaluatorClient}
import org.scalaexercises.evaluator.EvaluatorClient._
import org.scalaexercises.evaluator.implicits._
import org.scalaexercises.evaluator.EvaluatorResponses._
import org.scalajs.dom
import org.scalajs.dom.ext.PimpedNodeList
import org.scalajs.dom.raw.HTMLTextAreaElement
import org.querki.jquery._
import github4s.free.domain.GistFile
import github4s.Github
import Github._
import github4s.js.Implicits._

@JSExport
object KazariPlugin extends JSApp {
  import DOMHelper._

  val errorMessagePrefix = "Compilation error:"
  val newGistPrompt = "Choose a description for your Gist"
  val newGistDefaultDescription = "Gist created from Kazari (https://github.com/47deg/sbt-microsites)"
  val newGistFilename = "KazariGist.scala"
  lazy val codeSnippets = document.querySelectorAll(codeSnippetsSelector)

  @JSExport
  def main(): Unit = { }

  @JSExport
  def decorateCode(url: String, scalaEvalToken: String, githubToken: String, theme: String): Unit = {
    val textSnippets = generateCodeTextSnippets()
    lazy val evalClient = new EvaluatorClient(url, scalaEvalToken)

    val modalDiv = createModalDiv(codeModalClass)
    document.body.appendChild(modalDiv)
    applyModalStyles()

    val cmParams: EditorConfiguration = EditorConfig
        .mode("javascript")
        .lineNumbers(true)
        .theme(theme)

    val codeMirror = document.querySelector("#" + codeModalInternalTextArea) match {
      case el: HTMLTextAreaElement =>
        val m = CodeMirror.fromTextArea(el, cmParams)
        m.getDoc().setValue(textSnippets.last)
        m.setSize("100%", ($(window).height() * codeModalEditorMaxHeightPercent) / 100.0)

        val codeSnippetsFromModal = () => m.getDoc().getValue()

        addRunButtonBehaviour(
          s".$codeModalClass .$decoratorButtonRunClass",
          s".$codeModalClass",
          evalClient,
          codeSnippetsFromModal
        )

        addGistButtonBehavior(s".$decoratorButtonSaveGistClass",
          codeSnippetsFromModal,
          githubToken
        )

        Some(m)
      case _ => console.error("Couldn't find text area to embed CodeMirror instance."); None
    }

    codeSnippets.zipWithIndex foreach { case (node, i) =>
      val decoration = createDecoration(i)
      node.appendChild(decoration)

      val snippet = textSnippets.lift(i + 1)
      snippet foreach((s: String) => {
        addRunButtonBehaviour(
          s"#${decoration.id} .$decoratorButtonRunClass",
          s"#${decoration.id}",
          evalClient,
          () => s
        )
      })

      addClickListenerToButton(s"#${decoration.id} .$decoratorButtonEditClass", (e: dom.MouseEvent) => {
        val snippetStartLine = textSnippets.lift(snippetIndexFromDecorationId(decoration.id).getOrElse(0))
            .getOrElse("")
            .split("\n")
            .size

        codeMirror foreach { c =>
          codeMirrorScrollToLine(c, snippetStartLine)
        }
        $(".modal-state").prop("checked", true).change()
      })
    }
  }

  def generateCodeTextSnippets() =
    codeSnippets.map(_.textContent)
        .scanLeft("")((currentItem, result) =>
            if (currentItem == "") { result } else { currentItem + "\n" + result })


  def getDependenciesList(): List[Dependency] = {
    val content = getMetaContent(dependenciesMetaName)
    val elements = content.split(",")

    elements.foldRight(Seq[Dependency]()) { case (e, l) =>
      val split = e.split(";")
      if (split.length == 3) {
        l ++ Seq(Dependency(split(0), split(1), split(2)))
      } else {
        l
      }
    }.toList
  }

  def getResolversList(): List[String] = {
    val content = getMetaContent(resolversMetaName)
    content.split(",").toList
  }

  def sendEvaluatorRequest(evaluator: EvaluatorClient, codeSnippet: String): Future[EvaluationResponse[EvalResponse]] =
    evaluator.api.evaluates(
        dependencies = getDependenciesList(),
        resolvers = getResolversList(),
        code = codeSnippet).exec

  def addRunButtonBehaviour(btnSelector: String,
      parentSelector: String,
      evalClient: EvaluatorClient,
      codeSnippet: () => String,
      onSuccess: (EvaluationResponse[EvalResponse]) => Unit = (_) => (),
      onFailure: (Throwable) => Unit = (_) => ()): Unit =

    addClickListenerToButton(btnSelector, (e: dom.MouseEvent) => {
      def isEvaluationSuccessful(response: EvalResponse): Boolean = response.msg == EvalResponse.messages.ok

      changeButtonIcon(btnSelector + " " + "i", decoratorButtonPlayClass, decoratorButtonSpinnerClass)
      toggleButtonActiveState(btnSelector, true)
      hideAlertMessage(parentSelector)

      sendEvaluatorRequest(evalClient, codeSnippet()).onComplete {
        case Success(r) => {
          changeButtonIcon(btnSelector + " " + "i", decoratorButtonSpinnerClass, decoratorButtonPlayClass)
          toggleButtonActiveState(btnSelector, false)
          r.fold({ e =>
            showAlertMessage(parentSelector, s"$errorMessagePrefix ${e.getCause.getMessage}", false)
          }, { compilationResult => {
            val isSuccess = isEvaluationSuccessful(compilationResult.result)
            val resultMsg = compilationResult.result.value.getOrElse("")
            val errorMsg = if (!compilationResult.result.compilationInfos.isEmpty) {
              compilationResult.result.compilationInfos.mkString(" ")
            } else {
              resultMsg
            }
            val compilationValue = if (isSuccess) { resultMsg } else { errorMsg }
            showAlertMessage(parentSelector, s"${compilationResult.result.msg} - $compilationValue", isSuccess)
          }
          })
          onSuccess(r)
        }
        case Failure(e) => {
          changeButtonIcon(btnSelector + " " + "i", decoratorButtonSpinnerClass, decoratorButtonPlayClass)
          toggleButtonActiveState(btnSelector, false)
          showAlertMessage(parentSelector, "Error while connecting to the remote evaluator.", false)
          onFailure(e)
        }
      }
    })

  def addGistButtonBehavior(btnSelector: String,
      codeSnippet: () => String,
      accessToken: String,
      onSuccess: (EvaluationResponse[EvalResponse]) => Unit = (_) => (),
      onFailure: (Throwable) => Unit = (_) => ()
  ): Unit = {
    addClickListenerToButton(btnSelector, (e: dom.MouseEvent) => {
      changeButtonIcon(btnSelector + " " + "i", decoratorButtonGithubClass, decoratorButtonSpinnerClass)
      toggleButtonActiveState(btnSelector, true)

      val description = window.prompt(newGistPrompt)
      val gistApi = Github(Some(accessToken)).gists
      val files = Map(newGistFilename -> GistFile(codeSnippet()))
      val request = gistApi.newGist(if (description.isEmpty) newGistDefaultDescription else description, true, files)

      request.execFuture[SimpleHttpResponse]().onComplete { result =>
        changeButtonIcon(btnSelector + " " + "i", decoratorButtonSpinnerClass, decoratorButtonGithubClass)
        toggleButtonActiveState(btnSelector, false)

        result match {
          case Failure(e) => println("failure creating gist -> " + e)
          case Success(r) => r.fold(
            e => println("failure creating gist -> " + e),
            r => {
              println("Success creating gist -> " + r)
              println("Status code -> " + r.statusCode)
            }
          )
        }
      }
    })
  }

  def codeMirrorScrollToLine(editor: Editor, line: Int): Unit = {
    val pos = PositionBuilder(0, line)
    val scrollTop = editor.charCoords(pos, "local").asInstanceOf[CodeMirrorCharCords].top
    editor.scrollTo(0, scrollTop)
  }
}