# Validation scope

The current reference inventory is [V16](../profiles/v16-release.json).
[The V16 reproduction review](v16-reproduction-review.json) records this review's
offline reconstruction/checks and its remaining live boundaries.

Other JSON files in this directory retain historical source/build/audio/session
records. Their `_record_kind` and `_current_v16_acceptance` fields explicitly
prevent interpreting an old manual-handoff/final2/disconnect result as V16 proof.
A historical failure is retained as context and does not alone establish current
behavior. Read the current [acceptance limits](../docs/12-validation-and-open-issues.md)
and [owner matrix](../docs/16-owner-switching-tests.md) for live evidence.
