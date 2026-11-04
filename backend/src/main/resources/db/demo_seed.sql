-- Sequan demo seed: wipe transactional data and insert a clean demo workspace.
-- All demo users share password: password123
-- BCrypt hash generated for password123

BEGIN;

TRUNCATE TABLE
	webhook_events,
	audit_logs,
	notifications,
	disputes,
	refunds,
	releases,
	ledger_entries,
	payments,
	escrow_conditions,
	escrow_participants,
	escrows,
	refresh_tokens,
	users
RESTART IDENTITY CASCADE;

-- Users -----------------------------------------------------------------
INSERT INTO users (
	id, email, password_hash, full_name, global_role,
	stripe_customer_id, stripe_connect_account_id, connect_onboarding_complete,
	created_at, updated_at, deleted_at
) VALUES
(
	'11111111-1111-1111-1111-111111111101',
	'client@sequan.test',
	'$2b$10$8sFDa49bCK/lKqWzgNHhFu0FUw0rZcwFfhEw/LmPk7m.aOjXbdize',
	'Alex Client',
	'USER',
	'cus_VO5WE2oumtMHqP',
	'acct_1UNJHpK6B1xDbwsm',
	TRUE,
	NOW() - INTERVAL '40 days',
	NOW() - INTERVAL '1 day',
	NULL
),
(
	'11111111-1111-1111-1111-111111111102',
	'provider@sequan.test',
	'$2b$10$8sFDa49bCK/lKqWzgNHhFu0FUw0rZcwFfhEw/LmPk7m.aOjXbdize',
	'Jordan Provider',
	'USER',
	NULL,
	'acct_1UOgABKHkFBB5lRV',
	TRUE,
	NOW() - INTERVAL '40 days',
	NOW() - INTERVAL '1 day',
	NULL
),
(
	'11111111-1111-1111-1111-111111111103',
	'approver@sequan.test',
	'$2b$10$8sFDa49bCK/lKqWzgNHhFu0FUw0rZcwFfhEw/LmPk7m.aOjXbdize',
	'Morgan Approver',
	'USER',
	NULL,
	NULL,
	FALSE,
	NOW() - INTERVAL '30 days',
	NOW() - INTERVAL '2 days',
	NULL
),
(
	'11111111-1111-1111-1111-111111111104',
	'beneficiary@sequan.test',
	'$2b$10$8sFDa49bCK/lKqWzgNHhFu0FUw0rZcwFfhEw/LmPk7m.aOjXbdize',
	'Sam Beneficiary',
	'USER',
	NULL,
	NULL,
	FALSE,
	NOW() - INTERVAL '30 days',
	NOW() - INTERVAL '2 days',
	NULL
),
(
	'11111111-1111-1111-1111-111111111105',
	'arbitrator@sequan.test',
	'$2b$10$8sFDa49bCK/lKqWzgNHhFu0FUw0rZcwFfhEw/LmPk7m.aOjXbdize',
	'Casey Arbitrator',
	'USER',
	NULL,
	NULL,
	FALSE,
	NOW() - INTERVAL '30 days',
	NOW() - INTERVAL '2 days',
	NULL
),
(
	'11111111-1111-1111-1111-111111111106',
	'ashrefbenkassem@gmail.com',
	'$2b$10$8sFDa49bCK/lKqWzgNHhFu0FUw0rZcwFfhEw/LmPk7m.aOjXbdize',
	'Ashref Benkassem',
	'USER',
	NULL,
	'acct_1UOgICK4umeaGEXL',
	TRUE,
	NOW() - INTERVAL '35 days',
	NOW() - INTERVAL '1 day',
	NULL
);

-- Escrow 1: awaiting release (client must approve) ----------------------
INSERT INTO escrows (
	id, title, description, currency,
	amount_total, amount_funded, amount_released, amount_refunded, amount_available,
	status, funding_deadline, completion_deadline, created_by, version, created_at, updated_at
) VALUES (
	'22222222-2222-2222-2222-222222222201',
	'Website redesign',
	'Full redesign of marketing site, including homepage and pricing.',
	'USD',
	500000, 500000, 0, 0, 500000,
	'AWAITING_RELEASE',
	NOW() + INTERVAL '20 days',
	NOW() + INTERVAL '45 days',
	'11111111-1111-1111-1111-111111111101',
	3,
	NOW() - INTERVAL '18 days',
	NOW() - INTERVAL '1 day'
);

INSERT INTO escrow_participants (
	id, escrow_id, user_id, role, share_type, share_value,
	participation_status, approval_status, can_approve_release, invited_email, created_at, updated_at
) VALUES
(
	'33333333-3333-3333-3333-333333333301',
	'22222222-2222-2222-2222-222222222201',
	'11111111-1111-1111-1111-111111111101',
	'CLIENT', 'PERCENT_BPS', 0, 'ACCEPTED', 'PENDING', TRUE,
	'client@sequan.test', NOW() - INTERVAL '18 days', NOW() - INTERVAL '1 day'
),
(
	'33333333-3333-3333-3333-333333333302',
	'22222222-2222-2222-2222-222222222201',
	'11111111-1111-1111-1111-111111111102',
	'PROVIDER', 'PERCENT_BPS', 10000, 'ACCEPTED', 'NOT_REQUIRED', FALSE,
	'provider@sequan.test', NOW() - INTERVAL '18 days', NOW() - INTERVAL '12 days'
),
(
	'33333333-3333-3333-3333-333333333303',
	'22222222-2222-2222-2222-222222222201',
	'11111111-1111-1111-1111-111111111103',
	'APPROVER', 'PERCENT_BPS', 0, 'ACCEPTED', 'APPROVED', TRUE,
	'approver@sequan.test', NOW() - INTERVAL '18 days', NOW() - INTERVAL '1 day'
);

INSERT INTO escrow_conditions (
	id, escrow_id, title, description, sort_order, status, required, created_at, updated_at
) VALUES
(
	'44444444-4444-4444-4444-444444444401',
	'22222222-2222-2222-2222-222222222201',
	'Homepage designs approved',
	'Client signed off on Figma homepage.',
	0, 'SATISFIED', TRUE, NOW() - INTERVAL '18 days', NOW() - INTERVAL '5 days'
),
(
	'44444444-4444-4444-4444-444444444402',
	'22222222-2222-2222-2222-222222222201',
	'Site deployed to production',
	'Live URL verified by client.',
	1, 'SATISFIED', TRUE, NOW() - INTERVAL '18 days', NOW() - INTERVAL '3 days'
);

INSERT INTO payments (
	id, escrow_id, payer_user_id, amount, currency, status,
	stripe_checkout_session_id, stripe_payment_intent_id, stripe_charge_id,
	idempotency_key, failure_reason, created_at, updated_at
) VALUES (
	'55555555-5555-5555-5555-555555555501',
	'22222222-2222-2222-2222-222222222201',
	'11111111-1111-1111-1111-111111111101',
	500000, 'USD', 'SUCCEEDED',
	'cs_demo_website_redesign', 'pi_demo_website_redesign', 'ch_demo_website_redesign',
	'demo-fund-website-redesign', NULL,
	NOW() - INTERVAL '15 days', NOW() - INTERVAL '15 days'
);

INSERT INTO ledger_entries (
	id, escrow_id, entry_type, amount, currency, reference_type, reference_id,
	description, created_by, created_at
) VALUES (
	'66666666-6666-6666-6666-666666666601',
	'22222222-2222-2222-2222-222222222201',
	'FUND', 500000, 'USD', 'Payment', '55555555-5555-5555-5555-555555555501',
	'Escrow funded', '11111111-1111-1111-1111-111111111101', NOW() - INTERVAL '15 days'
);

INSERT INTO audit_logs (id, escrow_id, actor_user_id, action, entity_type, entity_id, metadata, created_at) VALUES
('77777777-7777-7777-7777-777777777701', '22222222-2222-2222-2222-222222222201', '11111111-1111-1111-1111-111111111101', 'ESCROW_CREATED', 'Escrow', '22222222-2222-2222-2222-222222222201', NULL, NOW() - INTERVAL '18 days'),
('77777777-7777-7777-7777-777777777702', '22222222-2222-2222-2222-222222222201', '11111111-1111-1111-1111-111111111101', 'ESCROW_PUBLISHED', 'Escrow', '22222222-2222-2222-2222-222222222201', NULL, NOW() - INTERVAL '17 days'),
('77777777-7777-7777-7777-777777777703', '22222222-2222-2222-2222-222222222201', NULL, 'ESCROW_READY_FOR_FUNDING', 'Escrow', '22222222-2222-2222-2222-222222222201', NULL, NOW() - INTERVAL '16 days'),
('77777777-7777-7777-7777-777777777704', '22222222-2222-2222-2222-222222222201', '11111111-1111-1111-1111-111111111101', 'PAYMENT_SUCCEEDED', 'Payment', '55555555-5555-5555-5555-555555555501', NULL, NOW() - INTERVAL '15 days'),
('77777777-7777-7777-7777-777777777705', '22222222-2222-2222-2222-222222222201', '11111111-1111-1111-1111-111111111102', 'ESCROW_STARTED', 'Escrow', '22222222-2222-2222-2222-222222222201', NULL, NOW() - INTERVAL '14 days'),
('77777777-7777-7777-7777-777777777706', '22222222-2222-2222-2222-222222222201', '11111111-1111-1111-1111-111111111101', 'CONDITION_SATISFIED', 'EscrowCondition', '44444444-4444-4444-4444-444444444401', NULL, NOW() - INTERVAL '5 days'),
('77777777-7777-7777-7777-777777777707', '22222222-2222-2222-2222-222222222201', '11111111-1111-1111-1111-111111111101', 'CONDITION_SATISFIED', 'EscrowCondition', '44444444-4444-4444-4444-444444444402', NULL, NOW() - INTERVAL '3 days'),
('77777777-7777-7777-7777-777777777708', '22222222-2222-2222-2222-222222222201', '11111111-1111-1111-1111-111111111102', 'RELEASE_REQUESTED', 'Escrow', '22222222-2222-2222-2222-222222222201', NULL, NOW() - INTERVAL '2 days'),
('77777777-7777-7777-7777-777777777709', '22222222-2222-2222-2222-222222222201', '11111111-1111-1111-1111-111111111103', 'RELEASE_APPROVED', 'EscrowParticipant', '33333333-3333-3333-3333-333333333303', NULL, NOW() - INTERVAL '1 day');

INSERT INTO notifications (id, user_id, escrow_id, type, title, message, read_at, created_at) VALUES
(
	'88888888-8888-8888-8888-888888888801',
	'11111111-1111-1111-1111-111111111101',
	'22222222-2222-2222-2222-222222222201',
	'RELEASE',
	'Release awaiting your approval',
	'Website redesign is ready for you to approve and release funds.',
	NULL,
	NOW() - INTERVAL '2 days'
);

-- Escrow 2: in progress (conditions pending) ----------------------------
INSERT INTO escrows (
	id, title, description, currency,
	amount_total, amount_funded, amount_released, amount_refunded, amount_available,
	status, funding_deadline, completion_deadline, created_by, version, created_at, updated_at
) VALUES (
	'22222222-2222-2222-2222-222222222202',
	'Mobile app MVP',
	'Build and ship an iOS/Android MVP for onboarding and payments.',
	'USD',
	1200000, 1200000, 0, 0, 1200000,
	'IN_PROGRESS',
	NOW() + INTERVAL '10 days',
	NOW() + INTERVAL '60 days',
	'11111111-1111-1111-1111-111111111101',
	2,
	NOW() - INTERVAL '12 days',
	NOW() - INTERVAL '4 days'
);

INSERT INTO escrow_participants (
	id, escrow_id, user_id, role, share_type, share_value,
	participation_status, approval_status, can_approve_release, invited_email, created_at, updated_at
) VALUES
(
	'33333333-3333-3333-3333-333333333311',
	'22222222-2222-2222-2222-222222222202',
	'11111111-1111-1111-1111-111111111101',
	'CLIENT', 'PERCENT_BPS', 0, 'ACCEPTED', 'NOT_REQUIRED', TRUE,
	'client@sequan.test', NOW() - INTERVAL '12 days', NOW() - INTERVAL '10 days'
),
(
	'33333333-3333-3333-3333-333333333312',
	'22222222-2222-2222-2222-222222222202',
	'11111111-1111-1111-1111-111111111106',
	'PROVIDER', 'PERCENT_BPS', 7000, 'ACCEPTED', 'NOT_REQUIRED', FALSE,
	'ashrefbenkassem@gmail.com', NOW() - INTERVAL '12 days', NOW() - INTERVAL '10 days'
),
(
	'33333333-3333-3333-3333-333333333313',
	'22222222-2222-2222-2222-222222222202',
	'11111111-1111-1111-1111-111111111104',
	'BENEFICIARY', 'PERCENT_BPS', 3000, 'ACCEPTED', 'NOT_REQUIRED', FALSE,
	'beneficiary@sequan.test', NOW() - INTERVAL '12 days', NOW() - INTERVAL '10 days'
);

INSERT INTO escrow_conditions (
	id, escrow_id, title, description, sort_order, status, required, created_at, updated_at
) VALUES
(
	'44444444-4444-4444-4444-444444444411',
	'22222222-2222-2222-2222-222222222202',
	'Alpha build delivered',
	'TestFlight / internal APK shared with client.',
	0, 'SATISFIED', TRUE, NOW() - INTERVAL '12 days', NOW() - INTERVAL '6 days'
),
(
	'44444444-4444-4444-4444-444444444412',
	'22222222-2222-2222-2222-222222222202',
	'Acceptance testing passed',
	'Client QA checklist complete.',
	1, 'PENDING', TRUE, NOW() - INTERVAL '12 days', NOW() - INTERVAL '12 days'
);

INSERT INTO payments (
	id, escrow_id, payer_user_id, amount, currency, status,
	stripe_checkout_session_id, stripe_payment_intent_id, stripe_charge_id,
	idempotency_key, failure_reason, created_at, updated_at
) VALUES (
	'55555555-5555-5555-5555-555555555502',
	'22222222-2222-2222-2222-222222222202',
	'11111111-1111-1111-1111-111111111101',
	1200000, 'USD', 'SUCCEEDED',
	'cs_demo_mobile_mvp', 'pi_demo_mobile_mvp', 'ch_demo_mobile_mvp',
	'demo-fund-mobile-mvp', NULL,
	NOW() - INTERVAL '9 days', NOW() - INTERVAL '9 days'
);

INSERT INTO ledger_entries (
	id, escrow_id, entry_type, amount, currency, reference_type, reference_id,
	description, created_by, created_at
) VALUES (
	'66666666-6666-6666-6666-666666666602',
	'22222222-2222-2222-2222-222222222202',
	'FUND', 1200000, 'USD', 'Payment', '55555555-5555-5555-5555-555555555502',
	'Escrow funded', '11111111-1111-1111-1111-111111111101', NOW() - INTERVAL '9 days'
);

INSERT INTO audit_logs (id, escrow_id, actor_user_id, action, entity_type, entity_id, metadata, created_at) VALUES
('77777777-7777-7777-7777-777777777711', '22222222-2222-2222-2222-222222222202', '11111111-1111-1111-1111-111111111101', 'ESCROW_CREATED', 'Escrow', '22222222-2222-2222-2222-222222222202', NULL, NOW() - INTERVAL '12 days'),
('77777777-7777-7777-7777-777777777712', '22222222-2222-2222-2222-222222222202', '11111111-1111-1111-1111-111111111101', 'ESCROW_PUBLISHED', 'Escrow', '22222222-2222-2222-2222-222222222202', NULL, NOW() - INTERVAL '11 days'),
('77777777-7777-7777-7777-777777777713', '22222222-2222-2222-2222-222222222202', NULL, 'ESCROW_READY_FOR_FUNDING', 'Escrow', '22222222-2222-2222-2222-222222222202', NULL, NOW() - INTERVAL '10 days'),
('77777777-7777-7777-7777-777777777714', '22222222-2222-2222-2222-222222222202', '11111111-1111-1111-1111-111111111101', 'PAYMENT_SUCCEEDED', 'Payment', '55555555-5555-5555-5555-555555555502', NULL, NOW() - INTERVAL '9 days'),
('77777777-7777-7777-7777-777777777715', '22222222-2222-2222-2222-222222222202', '11111111-1111-1111-1111-111111111106', 'ESCROW_STARTED', 'Escrow', '22222222-2222-2222-2222-222222222202', NULL, NOW() - INTERVAL '8 days'),
('77777777-7777-7777-7777-777777777716', '22222222-2222-2222-2222-222222222202', '11111111-1111-1111-1111-111111111101', 'CONDITION_SATISFIED', 'EscrowCondition', '44444444-4444-4444-4444-444444444411', NULL, NOW() - INTERVAL '6 days');

-- Escrow 3: pending funding --------------------------------------------
INSERT INTO escrows (
	id, title, description, currency,
	amount_total, amount_funded, amount_released, amount_refunded, amount_available,
	status, funding_deadline, completion_deadline, created_by, version, created_at, updated_at
) VALUES (
	'22222222-2222-2222-2222-222222222203',
	'Brand photography',
	'Half-day studio shoot plus edited asset delivery.',
	'USD',
	250000, 0, 0, 0, 0,
	'PENDING_FUNDING',
	NOW() + INTERVAL '7 days',
	NOW() + INTERVAL '30 days',
	'11111111-1111-1111-1111-111111111101',
	1,
	NOW() - INTERVAL '5 days',
	NOW() - INTERVAL '3 days'
);

INSERT INTO escrow_participants (
	id, escrow_id, user_id, role, share_type, share_value,
	participation_status, approval_status, can_approve_release, invited_email, created_at, updated_at
) VALUES
(
	'33333333-3333-3333-3333-333333333321',
	'22222222-2222-2222-2222-222222222203',
	'11111111-1111-1111-1111-111111111101',
	'CLIENT', 'PERCENT_BPS', 0, 'ACCEPTED', 'NOT_REQUIRED', TRUE,
	'client@sequan.test', NOW() - INTERVAL '5 days', NOW() - INTERVAL '4 days'
),
(
	'33333333-3333-3333-3333-333333333322',
	'22222222-2222-2222-2222-222222222203',
	'11111111-1111-1111-1111-111111111102',
	'PROVIDER', 'PERCENT_BPS', 10000, 'ACCEPTED', 'NOT_REQUIRED', FALSE,
	'provider@sequan.test', NOW() - INTERVAL '5 days', NOW() - INTERVAL '3 days'
);

INSERT INTO escrow_conditions (
	id, escrow_id, title, description, sort_order, status, required, created_at, updated_at
) VALUES (
	'44444444-4444-4444-4444-444444444421',
	'22222222-2222-2222-2222-222222222203',
	'Final gallery delivered',
	'All selected shots edited and uploaded.',
	0, 'PENDING', TRUE, NOW() - INTERVAL '5 days', NOW() - INTERVAL '5 days'
);

INSERT INTO audit_logs (id, escrow_id, actor_user_id, action, entity_type, entity_id, metadata, created_at) VALUES
('77777777-7777-7777-7777-777777777721', '22222222-2222-2222-2222-222222222203', '11111111-1111-1111-1111-111111111101', 'ESCROW_CREATED', 'Escrow', '22222222-2222-2222-2222-222222222203', NULL, NOW() - INTERVAL '5 days'),
('77777777-7777-7777-7777-777777777722', '22222222-2222-2222-2222-222222222203', '11111111-1111-1111-1111-111111111101', 'ESCROW_PUBLISHED', 'Escrow', '22222222-2222-2222-2222-222222222203', NULL, NOW() - INTERVAL '4 days'),
('77777777-7777-7777-7777-777777777723', '22222222-2222-2222-2222-222222222203', NULL, 'ESCROW_READY_FOR_FUNDING', 'Escrow', '22222222-2222-2222-2222-222222222203', NULL, NOW() - INTERVAL '3 days');

-- Escrow 4: pending participants ---------------------------------------
INSERT INTO escrows (
	id, title, description, currency,
	amount_total, amount_funded, amount_released, amount_refunded, amount_available,
	status, funding_deadline, completion_deadline, created_by, version, created_at, updated_at
) VALUES (
	'22222222-2222-2222-2222-222222222204',
	'Logo package',
	'Primary logo, wordmark, and brand color guidelines.',
	'USD',
	150000, 0, 0, 0, 0,
	'PENDING_PARTICIPANTS',
	NOW() + INTERVAL '14 days',
	NOW() + INTERVAL '40 days',
	'11111111-1111-1111-1111-111111111101',
	1,
	NOW() - INTERVAL '2 days',
	NOW() - INTERVAL '2 days'
);

INSERT INTO escrow_participants (
	id, escrow_id, user_id, role, share_type, share_value,
	participation_status, approval_status, can_approve_release, invited_email, created_at, updated_at
) VALUES
(
	'33333333-3333-3333-3333-333333333331',
	'22222222-2222-2222-2222-222222222204',
	'11111111-1111-1111-1111-111111111101',
	'CLIENT', 'PERCENT_BPS', 0, 'ACCEPTED', 'NOT_REQUIRED', TRUE,
	'client@sequan.test', NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days'
),
(
	'33333333-3333-3333-3333-333333333332',
	'22222222-2222-2222-2222-222222222204',
	'11111111-1111-1111-1111-111111111102',
	'PROVIDER', 'PERCENT_BPS', 10000, 'PENDING', 'NOT_REQUIRED', FALSE,
	'provider@sequan.test', NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days'
);

INSERT INTO escrow_conditions (
	id, escrow_id, title, description, sort_order, status, required, created_at, updated_at
) VALUES (
	'44444444-4444-4444-4444-444444444431',
	'22222222-2222-2222-2222-222222222204',
	'Final logo files delivered',
	'SVG, PNG, and PDF exports.',
	0, 'PENDING', TRUE, NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days'
);

INSERT INTO audit_logs (id, escrow_id, actor_user_id, action, entity_type, entity_id, metadata, created_at) VALUES
('77777777-7777-7777-7777-777777777731', '22222222-2222-2222-2222-222222222204', '11111111-1111-1111-1111-111111111101', 'ESCROW_CREATED', 'Escrow', '22222222-2222-2222-2222-222222222204', NULL, NOW() - INTERVAL '2 days'),
('77777777-7777-7777-7777-777777777732', '22222222-2222-2222-2222-222222222204', '11111111-1111-1111-1111-111111111101', 'ESCROW_PUBLISHED', 'Escrow', '22222222-2222-2222-2222-222222222204', NULL, NOW() - INTERVAL '2 days');

INSERT INTO notifications (id, user_id, escrow_id, type, title, message, read_at, created_at) VALUES
(
	'88888888-8888-8888-8888-888888888802',
	'11111111-1111-1111-1111-111111111102',
	'22222222-2222-2222-2222-222222222204',
	'INVITE',
	'Escrow invitation',
	'You have been invited to participate in: Logo package',
	NULL,
	NOW() - INTERVAL '2 days'
);

-- Escrow 5: released (completed) ---------------------------------------
INSERT INTO escrows (
	id, title, description, currency,
	amount_total, amount_funded, amount_released, amount_refunded, amount_available,
	status, funding_deadline, completion_deadline, created_by, version, created_at, updated_at
) VALUES (
	'22222222-2222-2222-2222-222222222205',
	'SEO retainer — September',
	'Monthly technical SEO and content brief package.',
	'USD',
	300000, 300000, 300000, 0, 0,
	'RELEASED',
	NOW() - INTERVAL '20 days',
	NOW() - INTERVAL '5 days',
	'11111111-1111-1111-1111-111111111101',
	4,
	NOW() - INTERVAL '35 days',
	NOW() - INTERVAL '8 days'
);

INSERT INTO escrow_participants (
	id, escrow_id, user_id, role, share_type, share_value,
	participation_status, approval_status, can_approve_release, invited_email, created_at, updated_at
) VALUES
(
	'33333333-3333-3333-3333-333333333341',
	'22222222-2222-2222-2222-222222222205',
	'11111111-1111-1111-1111-111111111101',
	'CLIENT', 'PERCENT_BPS', 0, 'ACCEPTED', 'APPROVED', TRUE,
	'client@sequan.test', NOW() - INTERVAL '35 days', NOW() - INTERVAL '9 days'
),
(
	'33333333-3333-3333-3333-333333333342',
	'22222222-2222-2222-2222-222222222205',
	'11111111-1111-1111-1111-111111111102',
	'PROVIDER', 'PERCENT_BPS', 10000, 'ACCEPTED', 'NOT_REQUIRED', FALSE,
	'provider@sequan.test', NOW() - INTERVAL '35 days', NOW() - INTERVAL '30 days'
);

INSERT INTO escrow_conditions (
	id, escrow_id, title, description, sort_order, status, required, created_at, updated_at
) VALUES (
	'44444444-4444-4444-4444-444444444441',
	'22222222-2222-2222-2222-222222222205',
	'Monthly report delivered',
	'Rankings, fixes, and next-month plan.',
	0, 'SATISFIED', TRUE, NOW() - INTERVAL '35 days', NOW() - INTERVAL '10 days'
);

INSERT INTO payments (
	id, escrow_id, payer_user_id, amount, currency, status,
	stripe_checkout_session_id, stripe_payment_intent_id, stripe_charge_id,
	idempotency_key, failure_reason, created_at, updated_at
) VALUES (
	'55555555-5555-5555-5555-555555555505',
	'22222222-2222-2222-2222-222222222205',
	'11111111-1111-1111-1111-111111111101',
	300000, 'USD', 'SUCCEEDED',
	'cs_demo_seo_sept', 'pi_demo_seo_sept', 'ch_demo_seo_sept',
	'demo-fund-seo-sept', NULL,
	NOW() - INTERVAL '28 days', NOW() - INTERVAL '28 days'
);

INSERT INTO releases (
	id, escrow_id, requested_by, beneficiary_participant_id, amount, currency, status,
	stripe_transfer_id, idempotency_key, failure_reason, created_at, updated_at
) VALUES (
	'99999999-9999-9999-9999-999999999901',
	'22222222-2222-2222-2222-222222222205',
	'11111111-1111-1111-1111-111111111101',
	'33333333-3333-3333-3333-333333333342',
	300000, 'USD', 'COMPLETED',
	'tr_demo_seo_sept', 'demo-release-seo-sept', NULL,
	NOW() - INTERVAL '8 days', NOW() - INTERVAL '8 days'
);

INSERT INTO ledger_entries (
	id, escrow_id, entry_type, amount, currency, reference_type, reference_id,
	description, created_by, created_at
) VALUES
(
	'66666666-6666-6666-6666-666666666605',
	'22222222-2222-2222-2222-222222222205',
	'FUND', 300000, 'USD', 'Payment', '55555555-5555-5555-5555-555555555505',
	'Escrow funded', '11111111-1111-1111-1111-111111111101', NOW() - INTERVAL '28 days'
),
(
	'66666666-6666-6666-6666-666666666606',
	'22222222-2222-2222-2222-222222222205',
	'RELEASE', -300000, 'USD', 'Release', '99999999-9999-9999-9999-999999999901',
	'Funds released to participant', '11111111-1111-1111-1111-111111111101', NOW() - INTERVAL '8 days'
);

INSERT INTO audit_logs (id, escrow_id, actor_user_id, action, entity_type, entity_id, metadata, created_at) VALUES
('77777777-7777-7777-7777-777777777741', '22222222-2222-2222-2222-222222222205', '11111111-1111-1111-1111-111111111101', 'ESCROW_CREATED', 'Escrow', '22222222-2222-2222-2222-222222222205', NULL, NOW() - INTERVAL '35 days'),
('77777777-7777-7777-7777-777777777742', '22222222-2222-2222-2222-222222222205', '11111111-1111-1111-1111-111111111101', 'ESCROW_PUBLISHED', 'Escrow', '22222222-2222-2222-2222-222222222205', NULL, NOW() - INTERVAL '34 days'),
('77777777-7777-7777-7777-777777777743', '22222222-2222-2222-2222-222222222205', NULL, 'ESCROW_READY_FOR_FUNDING', 'Escrow', '22222222-2222-2222-2222-222222222205', NULL, NOW() - INTERVAL '33 days'),
('77777777-7777-7777-7777-777777777744', '22222222-2222-2222-2222-222222222205', '11111111-1111-1111-1111-111111111101', 'PAYMENT_SUCCEEDED', 'Payment', '55555555-5555-5555-5555-555555555505', NULL, NOW() - INTERVAL '28 days'),
('77777777-7777-7777-7777-777777777745', '22222222-2222-2222-2222-222222222205', '11111111-1111-1111-1111-111111111102', 'ESCROW_STARTED', 'Escrow', '22222222-2222-2222-2222-222222222205', NULL, NOW() - INTERVAL '27 days'),
('77777777-7777-7777-7777-777777777746', '22222222-2222-2222-2222-222222222205', '11111111-1111-1111-1111-111111111101', 'CONDITION_SATISFIED', 'EscrowCondition', '44444444-4444-4444-4444-444444444441', NULL, NOW() - INTERVAL '10 days'),
('77777777-7777-7777-7777-777777777747', '22222222-2222-2222-2222-222222222205', '11111111-1111-1111-1111-111111111102', 'RELEASE_REQUESTED', 'Escrow', '22222222-2222-2222-2222-222222222205', NULL, NOW() - INTERVAL '9 days'),
('77777777-7777-7777-7777-777777777748', '22222222-2222-2222-2222-222222222205', '11111111-1111-1111-1111-111111111101', 'RELEASE_APPROVED', 'EscrowParticipant', '33333333-3333-3333-3333-333333333341', NULL, NOW() - INTERVAL '9 days'),
('77777777-7777-7777-7777-777777777749', '22222222-2222-2222-2222-222222222205', '11111111-1111-1111-1111-111111111101', 'RELEASE_COMPLETED', 'Release', '99999999-9999-9999-9999-999999999901', NULL, NOW() - INTERVAL '8 days');

-- Escrow 6: cancelled --------------------------------------------------
INSERT INTO escrows (
	id, title, description, currency,
	amount_total, amount_funded, amount_released, amount_refunded, amount_available,
	status, funding_deadline, completion_deadline, created_by, version, created_at, updated_at
) VALUES (
	'22222222-2222-2222-2222-222222222206',
	'Pitch deck polish',
	'Cancelled after scope change — never funded.',
	'USD',
	80000, 0, 0, 0, 0,
	'CANCELLED',
	NOW() - INTERVAL '3 days',
	NOW() + INTERVAL '20 days',
	'11111111-1111-1111-1111-111111111101',
	1,
	NOW() - INTERVAL '10 days',
	NOW() - INTERVAL '6 days'
);

INSERT INTO escrow_participants (
	id, escrow_id, user_id, role, share_type, share_value,
	participation_status, approval_status, can_approve_release, invited_email, created_at, updated_at
) VALUES
(
	'33333333-3333-3333-3333-333333333351',
	'22222222-2222-2222-2222-222222222206',
	'11111111-1111-1111-1111-111111111101',
	'CLIENT', 'PERCENT_BPS', 0, 'ACCEPTED', 'NOT_REQUIRED', TRUE,
	'client@sequan.test', NOW() - INTERVAL '10 days', NOW() - INTERVAL '6 days'
),
(
	'33333333-3333-3333-3333-333333333352',
	'22222222-2222-2222-2222-222222222206',
	'11111111-1111-1111-1111-111111111102',
	'PROVIDER', 'PERCENT_BPS', 10000, 'DECLINED', 'NOT_REQUIRED', FALSE,
	'provider@sequan.test', NOW() - INTERVAL '10 days', NOW() - INTERVAL '6 days'
);

INSERT INTO escrow_conditions (
	id, escrow_id, title, description, sort_order, status, required, created_at, updated_at
) VALUES (
	'44444444-4444-4444-4444-444444444451',
	'22222222-2222-2222-2222-222222222206',
	'Final slides delivered',
	NULL,
	0, 'PENDING', TRUE, NOW() - INTERVAL '10 days', NOW() - INTERVAL '10 days'
);

INSERT INTO audit_logs (id, escrow_id, actor_user_id, action, entity_type, entity_id, metadata, created_at) VALUES
('77777777-7777-7777-7777-777777777751', '22222222-2222-2222-2222-222222222206', '11111111-1111-1111-1111-111111111101', 'ESCROW_CREATED', 'Escrow', '22222222-2222-2222-2222-222222222206', NULL, NOW() - INTERVAL '10 days'),
('77777777-7777-7777-7777-777777777752', '22222222-2222-2222-2222-222222222206', '11111111-1111-1111-1111-111111111101', 'ESCROW_PUBLISHED', 'Escrow', '22222222-2222-2222-2222-222222222206', NULL, NOW() - INTERVAL '9 days'),
('77777777-7777-7777-7777-777777777753', '22222222-2222-2222-2222-222222222206', '11111111-1111-1111-1111-111111111102', 'PARTICIPANT_DECLINED', 'EscrowParticipant', '33333333-3333-3333-3333-333333333352', NULL, NOW() - INTERVAL '6 days'),
('77777777-7777-7777-7777-777777777754', '22222222-2222-2222-2222-222222222206', '11111111-1111-1111-1111-111111111102', 'ESCROW_CANCELLED', 'Escrow', '22222222-2222-2222-2222-222222222206', NULL, NOW() - INTERVAL '6 days');

-- Escrow 7: funded, ready to start -------------------------------------
INSERT INTO escrows (
	id, title, description, currency,
	amount_total, amount_funded, amount_released, amount_refunded, amount_available,
	status, funding_deadline, completion_deadline, created_by, version, created_at, updated_at
) VALUES (
	'22222222-2222-2222-2222-222222222207',
	'Consulting sprint',
	'Two-week product discovery and roadmap workshop.',
	'USD',
	450000, 450000, 0, 0, 450000,
	'FUNDED',
	NOW() + INTERVAL '5 days',
	NOW() + INTERVAL '25 days',
	'11111111-1111-1111-1111-111111111101',
	2,
	NOW() - INTERVAL '7 days',
	NOW() - INTERVAL '1 day'
);

INSERT INTO escrow_participants (
	id, escrow_id, user_id, role, share_type, share_value,
	participation_status, approval_status, can_approve_release, invited_email, created_at, updated_at
) VALUES
(
	'33333333-3333-3333-3333-333333333361',
	'22222222-2222-2222-2222-222222222207',
	'11111111-1111-1111-1111-111111111101',
	'CLIENT', 'PERCENT_BPS', 0, 'ACCEPTED', 'NOT_REQUIRED', TRUE,
	'client@sequan.test', NOW() - INTERVAL '7 days', NOW() - INTERVAL '5 days'
),
(
	'33333333-3333-3333-3333-333333333362',
	'22222222-2222-2222-2222-222222222207',
	'11111111-1111-1111-1111-111111111102',
	'PROVIDER', 'PERCENT_BPS', 10000, 'ACCEPTED', 'NOT_REQUIRED', FALSE,
	'provider@sequan.test', NOW() - INTERVAL '7 days', NOW() - INTERVAL '5 days'
),
(
	'33333333-3333-3333-3333-333333333363',
	'22222222-2222-2222-2222-222222222207',
	'11111111-1111-1111-1111-111111111105',
	'ARBITRATOR', 'PERCENT_BPS', 0, 'ACCEPTED', 'NOT_REQUIRED', FALSE,
	'arbitrator@sequan.test', NOW() - INTERVAL '7 days', NOW() - INTERVAL '5 days'
);

INSERT INTO escrow_conditions (
	id, escrow_id, title, description, sort_order, status, required, created_at, updated_at
) VALUES (
	'44444444-4444-4444-4444-444444444461',
	'22222222-2222-2222-2222-222222222207',
	'Workshop readout accepted',
	'Shared notes and prioritized backlog.',
	0, 'PENDING', TRUE, NOW() - INTERVAL '7 days', NOW() - INTERVAL '7 days'
);

INSERT INTO payments (
	id, escrow_id, payer_user_id, amount, currency, status,
	stripe_checkout_session_id, stripe_payment_intent_id, stripe_charge_id,
	idempotency_key, failure_reason, created_at, updated_at
) VALUES (
	'55555555-5555-5555-5555-555555555507',
	'22222222-2222-2222-2222-222222222207',
	'11111111-1111-1111-1111-111111111101',
	450000, 'USD', 'SUCCEEDED',
	'cs_demo_consulting', 'pi_demo_consulting', 'ch_demo_consulting',
	'demo-fund-consulting', NULL,
	NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day'
);

INSERT INTO ledger_entries (
	id, escrow_id, entry_type, amount, currency, reference_type, reference_id,
	description, created_by, created_at
) VALUES (
	'66666666-6666-6666-6666-666666666607',
	'22222222-2222-2222-2222-222222222207',
	'FUND', 450000, 'USD', 'Payment', '55555555-5555-5555-5555-555555555507',
	'Escrow funded', '11111111-1111-1111-1111-111111111101', NOW() - INTERVAL '1 day'
);

INSERT INTO audit_logs (id, escrow_id, actor_user_id, action, entity_type, entity_id, metadata, created_at) VALUES
('77777777-7777-7777-7777-777777777761', '22222222-2222-2222-2222-222222222207', '11111111-1111-1111-1111-111111111101', 'ESCROW_CREATED', 'Escrow', '22222222-2222-2222-2222-222222222207', NULL, NOW() - INTERVAL '7 days'),
('77777777-7777-7777-7777-777777777762', '22222222-2222-2222-2222-222222222207', '11111111-1111-1111-1111-111111111101', 'ESCROW_PUBLISHED', 'Escrow', '22222222-2222-2222-2222-222222222207', NULL, NOW() - INTERVAL '6 days'),
('77777777-7777-7777-7777-777777777763', '22222222-2222-2222-2222-222222222207', NULL, 'ESCROW_READY_FOR_FUNDING', 'Escrow', '22222222-2222-2222-2222-222222222207', NULL, NOW() - INTERVAL '5 days'),
('77777777-7777-7777-7777-777777777764', '22222222-2222-2222-2222-222222222207', '11111111-1111-1111-1111-111111111101', 'PAYMENT_SUCCEEDED', 'Payment', '55555555-5555-5555-5555-555555555507', NULL, NOW() - INTERVAL '1 day');

-- Escrow 8: draft (client only) ----------------------------------------
INSERT INTO escrows (
	id, title, description, currency,
	amount_total, amount_funded, amount_released, amount_refunded, amount_available,
	status, funding_deadline, completion_deadline, created_by, version, created_at, updated_at
) VALUES (
	'22222222-2222-2222-2222-222222222208',
	'Content calendar — Q1',
	'Draft only. Add participants before publishing.',
	'USD',
	180000, 0, 0, 0, 0,
	'DRAFT',
	NOW() + INTERVAL '21 days',
	NOW() + INTERVAL '90 days',
	'11111111-1111-1111-1111-111111111101',
	0,
	NOW() - INTERVAL '1 day',
	NOW() - INTERVAL '1 hour'
);

INSERT INTO escrow_participants (
	id, escrow_id, user_id, role, share_type, share_value,
	participation_status, approval_status, can_approve_release, invited_email, created_at, updated_at
) VALUES (
	'33333333-3333-3333-3333-333333333371',
	'22222222-2222-2222-2222-222222222208',
	'11111111-1111-1111-1111-111111111101',
	'CLIENT', 'PERCENT_BPS', 0, 'ACCEPTED', 'NOT_REQUIRED', TRUE,
	'client@sequan.test', NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day'
);

INSERT INTO escrow_conditions (
	id, escrow_id, title, description, sort_order, status, required, created_at, updated_at
) VALUES (
	'44444444-4444-4444-4444-444444444471',
	'22222222-2222-2222-2222-222222222208',
	'Editorial calendar approved',
	NULL,
	0, 'PENDING', TRUE, NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day'
);

INSERT INTO audit_logs (id, escrow_id, actor_user_id, action, entity_type, entity_id, metadata, created_at) VALUES
('77777777-7777-7777-7777-777777777771', '22222222-2222-2222-2222-222222222208', '11111111-1111-1111-1111-111111111101', 'ESCROW_CREATED', 'Escrow', '22222222-2222-2222-2222-222222222208', NULL, NOW() - INTERVAL '1 day');

COMMIT;
