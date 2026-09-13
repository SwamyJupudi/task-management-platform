/**
 * The workspace feature's public surface.
 *
 * The router needs the settings screen and nothing else. The hooks, the API
 * calls and the two cards are internals and stay that way.
 *
 * Creating, listing and deleting workspaces are not here. All three are gated
 * on a platform permission that no workspace role holds, so they belong to the
 * admin panel.
 */

export { WorkspaceSettingsPage } from './pages/workspace-settings-page'
