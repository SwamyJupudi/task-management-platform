import { destinationOf, labelOfType } from './destination'
import type { Notification } from './types'

/**
 * Where following a notification goes.
 *
 * Three entity types with three different answers, and a guard that decides
 * whether a row is a link at all. A wrong branch here produces a dead link
 * rather than an error, which is exactly the kind of bug nobody reports.
 */

const base: Notification = {
  id: 'n-1',
  type: 'task.assigned',
  message: 'Ada assigned you PLAT-12',
  actorUserId: 'u-1',
  actorName: 'Ada Lovelace',
  entityType: 'TASK',
  entityId: 'task-1',
  projectId: 'project-1',
  taskKey: 'PLAT-12',
  metadata: {},
  readAt: null,
  createdAt: '2026-09-13T00:00:00Z',
}

const notification = (over: Partial<Notification>): Notification => ({ ...base, ...over })

describe('destinationOf', () => {
  it('sends a task notification to the task', () => {
    expect(destinationOf(base, 'acme')).toBe('/w/acme/tasks/task-1')
  })

  it('sends a comment notification to its task, opened on the discussion', () => {
    // `entityId` is the comment and there is no route to one; the task arrives
    // in the metadata and is the only way to reach it.
    const comment = notification({
      entityType: 'COMMENT',
      entityId: 'comment-1',
      metadata: { taskId: 'task-9' },
    })

    expect(destinationOf(comment, 'acme')).toBe('/w/acme/tasks/task-9?panel=comments')
  })

  it('sends a project notification to the project', () => {
    const project = notification({
      entityType: 'PROJECT',
      entityId: 'project-1',
      taskKey: null,
    })

    expect(destinationOf(project, 'acme')).toBe('/w/acme/projects/project-1')
  })

  it('refuses a task row whose key the server no longer resolves', () => {
    // No key means no longer reachable, whatever the row still says. A link
    // would land on a 404.
    expect(destinationOf(notification({ taskKey: null }), 'acme')).toBeNull()
  })

  it('refuses a comment row whose key the server no longer resolves', () => {
    const comment = notification({
      entityType: 'COMMENT',
      taskKey: null,
      metadata: { taskId: 'task-9' },
    })

    expect(destinationOf(comment, 'acme')).toBeNull()
  })

  it('refuses a comment row with no usable task in its metadata', () => {
    expect(destinationOf(notification({ entityType: 'COMMENT', metadata: {} }), 'acme')).toBeNull()
    expect(
      destinationOf(notification({ entityType: 'COMMENT', metadata: { taskId: 42 } }), 'acme'),
    ).toBeNull()
    expect(
      destinationOf(notification({ entityType: 'COMMENT', metadata: { taskId: '' } }), 'acme'),
    ).toBeNull()
  })

  it('does not apply the key guard to project rows, which never carry one', () => {
    // Gating on it there would make every project notification unfollowable.
    const project = notification({ entityType: 'PROJECT', taskKey: null })
    expect(destinationOf(project, 'acme')).not.toBeNull()
  })

  it('refuses everything when the workspace slug is not known yet', () => {
    expect(destinationOf(base, '')).toBeNull()
  })

  it('encodes a slug that needs it', () => {
    expect(destinationOf(base, 'a b')).toContain('a%20b')
  })
})

describe('labelOfType', () => {
  it('names each of the seven triggers', () => {
    expect(labelOfType('task.assigned')).toBe('Assigned')
    expect(labelOfType('task.status_changed')).toBe('Status')
    expect(labelOfType('task.deadline_approaching')).toBe('Due soon')
    expect(labelOfType('comment.created')).toBe('Comment')
    expect(labelOfType('comment.mentioned')).toBe('Mention')
    expect(labelOfType('project.member_added')).toBe('Project')
    expect(labelOfType('project.status_changed')).toBe('Project')
  })

  it('falls back to the code so a type added later still reads as something', () => {
    expect(labelOfType('task.something_new')).toBe('task.something_new')
  })
})
