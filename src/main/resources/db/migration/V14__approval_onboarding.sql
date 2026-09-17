-- Onboarding by administrator approval, replacing onboarding by invitation.
--
-- The flow this migration makes room for:
--
--   register -> PENDING_APPROVAL -> an administrator approves, naming a
--   workspace and a role -> ACTIVE
--
-- Signing in no longer depends on a message arriving. That was the invitation
-- flow's single point of failure: an address that never received its link could
-- not join, and nothing in the product could tell the person why. Approval is
-- performed by somebody who is already signed in, so it cannot be lost in
-- transit.

-- ---------------------------------------------------------------------------
-- 1. The new status
-- ---------------------------------------------------------------------------
-- PENDING_VERIFICATION is folded into PENDING_APPROVAL rather than kept beside
-- it. Every row holding it is waiting on a gate that no longer exists, and
-- leaving them there would mean an account nobody can act on: no administrator
-- screen lists it, and confirming the address would no longer be enough to sign
-- in. They are exactly the people the new flow is for, so they join its queue.
UPDATE users SET status = 'PENDING_APPROVAL' WHERE status = 'PENDING_VERIFICATION';

ALTER TABLE users DROP CONSTRAINT users_status_check;
ALTER TABLE users
    ADD CONSTRAINT users_status_check
        CHECK (status IN ('PENDING_APPROVAL', 'ACTIVE', 'DEACTIVATED'));

-- ---------------------------------------------------------------------------
-- 2. ACTIVE no longer implies a confirmed address
-- ---------------------------------------------------------------------------
-- The old invariant said an account cannot be ACTIVE without email_verified_at,
-- which was right while confirming the address was what activated it. Approval
-- is what activates an account now, and an administrator must be able to
-- approve somebody whose address has not been confirmed -- often because the
-- demo or the deployment has no working mail transport at all.
--
-- Verification itself is NOT removed. /auth/verify-email still works and still
-- stamps email_verified_at; it is simply no longer a gate, and the column is
-- now a record of what happened rather than a precondition.
ALTER TABLE users DROP CONSTRAINT users_verified_when_active_check;

-- ---------------------------------------------------------------------------
-- 3. Who approved, and when
-- ---------------------------------------------------------------------------
-- Recorded on the row rather than left to the audit trail alone. "Who let this
-- person in" is the first question asked when an account turns out to have
-- reach it should not, and an answer that survives log retention is worth the
-- two columns.
ALTER TABLE users ADD COLUMN approved_at         timestamptz;
ALTER TABLE users ADD COLUMN approved_by_user_id uuid;

ALTER TABLE users
    ADD CONSTRAINT users_approved_by_fkey
        FOREIGN KEY (approved_by_user_id) REFERENCES users (id) ON DELETE SET NULL;

-- Both halves or neither, for the reason the platform role pair check exists:
-- a timestamp without an approver describes nothing anybody can follow up.
ALTER TABLE users
    ADD CONSTRAINT users_approval_pair_check
        CHECK ((approved_at IS NULL) = (approved_by_user_id IS NULL));

CREATE INDEX users_approved_by_user_id_idx ON users (approved_by_user_id);

-- ---------------------------------------------------------------------------
-- 4. Invitations are gone
-- ---------------------------------------------------------------------------
-- The table and everything hanging off it. Dropped rather than left dormant
-- because a table nobody writes and nobody reads is a table the next person has
-- to work out the status of; the schema should say plainly that this is not how
-- somebody joins a workspace any more.
--
-- Irreversible, and deliberately so: any invitation still outstanding when this
-- runs is discarded, and the person it was sent to registers and is approved
-- instead. Its indexes, constraints and foreign keys go with the table.
DROP TABLE IF EXISTS workspace_invitations;
