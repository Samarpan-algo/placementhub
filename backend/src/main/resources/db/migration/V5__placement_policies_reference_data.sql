-- V5: reference data that EVERY environment needs: one row per placement policy type.
--
-- Production starts with every policy OFF; the TPO turns them on in the Policies page.
-- updated_by stays NULL: on a new production database no admin exists yet when this runs.
-- ON CONFLICT (uq_placement_policies_type): dev and test databases already got these rows from the
-- demo seed (db/demo/V2), so nothing changes there.
INSERT INTO placement_policies (type, ctc_threshold_lpa, max_offers, active) VALUES
    ('ONE_OFFER',   NULL,  NULL, FALSE),
    ('DREAM_OFFER', 10.00, NULL, FALSE),
    ('MAX_OFFERS',  NULL,  2,    FALSE)
ON CONFLICT (type) DO NOTHING;
