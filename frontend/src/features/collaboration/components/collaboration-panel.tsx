import { useSearchParams } from 'react-router-dom'

import { Badge } from '@/components/ui/badge'
import { Card, CardContent } from '@/components/ui/card'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'

import { useAttachments } from '../hooks'
import { ActivityTimeline } from './activity-timeline'
import { AttachmentPanel } from './attachment-panel'
import { CommentThread } from './comment-thread'

/**
 * Discussion, files and history on one task.
 *
 * Three tabs rather than three stacked cards, because a task screen already
 * carries its details, its checklist and its dependencies; a thread that could
 * run to fifty comments underneath all of that would bury everything else.
 *
 * The chosen tab lives in the query string, so a link to "the files on this
 * task" is a link somebody can send, and a reload does not drop back to the
 * discussion. An unknown value falls back rather than erroring: a stale
 * bookmark is not a failure.
 *
 * The attachment listing is fetched once here and handed to both the thread and
 * the files tab. A task's files are one answer; asking for them twice would put
 * the two panels one refetch apart from each other.
 */

type Tab = 'comments' | 'files' | 'activity'

const TABS: readonly Tab[] = ['comments', 'files', 'activity']

function isTab(value: string | null): value is Tab {
  return value !== null && (TABS as readonly string[]).includes(value)
}

export function CollaborationPanel({
  taskId,
  projectId,
  /** Whether the signed-in person owns the task's project, for the delete rules. */
  ownsProject,
}: {
  taskId: string
  projectId: string
  ownsProject: boolean
}) {
  const [searchParams, setSearchParams] = useSearchParams()
  const attachments = useAttachments(taskId)

  const requested = searchParams.get('panel')
  const tab: Tab = isTab(requested) ? requested : 'comments'

  const files = attachments.data ?? []

  return (
    <Card>
      <CardContent>
        <Tabs
          value={tab}
          onValueChange={(next) => {
            const params = new URLSearchParams(searchParams)
            // The default leaves no trace: "?panel=comments" and no query
            // string are the same screen, and only one should be shareable.
            if (next === 'comments') params.delete('panel')
            else params.set('panel', next)
            setSearchParams(params, { replace: true })
          }}
          className="space-y-4"
        >
          <TabsList>
            <TabsTrigger value="comments">Comments</TabsTrigger>
            <TabsTrigger value="files">
              Files
              {files.length > 0 ? (
                <Badge variant="outline" className="ml-1.5 tabular-nums">
                  {files.length}
                </Badge>
              ) : null}
            </TabsTrigger>
            <TabsTrigger value="activity">Activity</TabsTrigger>
          </TabsList>

          <TabsContent value="comments">
            <CommentThread
              taskId={taskId}
              projectId={projectId}
              attachments={files}
              ownsProject={ownsProject}
            />
          </TabsContent>

          <TabsContent value="files">
            <AttachmentPanel taskId={taskId} ownsProject={ownsProject} />
          </TabsContent>

          <TabsContent value="activity">
            <ActivityTimeline taskId={taskId} />
          </TabsContent>
        </Tabs>
      </CardContent>
    </Card>
  )
}
