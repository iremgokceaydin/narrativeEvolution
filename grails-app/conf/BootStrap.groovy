import edu.msu.mi.gwurk.GwurkEvent
import edu.msu.mi.gwurk.SingleHitTask
import edu.msu.mi.gwurk.Task
import edu.msu.mi.gwurk.Workflow
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import groovy.util.logging.Log4j
import nardiff.mt.NardiffStuff
import nardiff.mt.Narrative
import nardiff.mt.NarrativeData
import nardiff.mt.NarrativeSeed
import org.springframework.web.context.WebApplicationContext
import org.springframework.web.context.support.WebApplicationContextUtils

import javax.servlet.ServletContext

//This is test application
@Log4j
class BootStrap {

    def mturkTaskService

    def grailsApplication

    def nardiffService


    public static Narrative insertStory(def storydata) {

        NarrativeSeed seed = new NarrativeSeed(distractorTask: new JsonBuilder(storydata.distractor).toPrettyString(),
                expandingLevel: 0, title: storydata.title, text: storydata.story)
        NarrativeSeed.withTransaction {
            seed.save flush: true, failOnError: true
        }

        Narrative result = new Narrative(seed)

        Narrative.withTransaction { tx ->
            result.save flush: true, failOnError: true
        }

        NarrativeData data = result.data

        if (!data) {
            println "Well this is odd"
        }

        data.text = seed.text

        NarrativeData.withTransaction {tx->
            data.save flush:true, failOnError:true
        }

        result

    }


    def init = { servletContext ->

        nardiffService.setBranching([2, 2, 2])

        def experimentData = new JsonSlurper().parse(new File(servletContext.getRealPath("/data/experimentAll.json")))

        experimentData.each { Map m ->
            if (!NarrativeSeed.findAllByTitle(m.title)) {
                insertStory(m)
            }
        }
        Workflow w
        if (Workflow.count() < 1) {
            w = new Workflow("Narrative Diffusion", "An experiment similar to a game of telephone", [
                    rewardAmount      : 0.58f,
                    relaunchInterval  : 1000 * 60 * 60,
                    autoApprove       : true,
                    lifetime          : 60 * 60 * 10,
                    assignmentDuration: 600,
                    keywords          : "research, memory, study, experiment",
                    maxAssignments    : 10,
                    height            : 1000,
                    requireApproval   : true
            ]).save([flush: true, failOnError: true])
            w.initStartingTasks((1..NarrativeSeed.count).collect { id ->
                new SingleHitTask("Narrative Task #" + id, [
                        controller : "narrative",
                        action     : "turkerTask",
                        title      : "Read a story and try to tell it to someone else.",
                        description: "This research is to help understand certain aspects of how information spreads.  No sensitive information is collected, and identities will be erased after data collection is complete.",
                ]).save([flush: true, failOnError: true])
            } as Task[])
            w.save()
        } else {
            w = Workflow.list().first()
        }

        w.startingTasks.each { Task t ->
            mturkTaskService.installTask(t) { type, GwurkEvent evt ->
                switch (type) {

                    case GwurkEvent.Type.TASK_STARTING:
                        log.info("Task # ${t.id} starting")
                        break

                    case GwurkEvent.Type.HIT_COMPLETE:
                        log.info("Hit complete!")
                        break
                    case GwurkEvent.Type.ASSIGNMENT_COMPLETE:
                        log.info("Assignment complete!")
                        log.info("Answer = " + evt.assignmentView.answer);

//                        NardiffStuff n = new NardiffStuff();
//                        n.doInsert(evt.assignmentView);

                        break;

                    case GwurkEvent.Type.TASK_COMPLETE:
                        log.info("Task complete!")
                        break
                }

            }
        }


        mturkTaskService.installWorkflow(w) { a, b ->
            log.info("Workflow complete!")
        }


        log.info("Bootstrap Complete")
    }

    def destroy = {
    }
}
