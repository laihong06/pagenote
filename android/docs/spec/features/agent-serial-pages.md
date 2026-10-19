# Android Agent Serial Page Runs

## Scope

Run a page list through the existing one-page Agent executor sequentially. The summary must reflect only requested pages that actually completed or changed. This policy is part of the Android Agent contract and does not replace the later app integration with project-backed tools.

## Behavior

- The page list must be non-empty, contain only positive page numbers, and contain no duplicates. Invalid input is rejected before execution begins.
- Pages execute in the input order, one at a time.
- A page is completed only when its executor returns `Done` containing that requested page. A `Done` event that omits the current page marks it incomplete.
- Reported page IDs outside the current request are ignored. Only the current requested page can be reported as updated by its one-page run.
- A returned failure or non-terminal tool call marks the current page incomplete and processing continues.
- A thrown non-cancellation exception marks the current page incomplete and processing continues. Coroutine cancellation is rethrown.
- A rate-limit failure marks the current and remaining pages incomplete, stops execution immediately, and sets `stoppedByRateLimit`.
- Completed, updated, and incomplete page lists preserve request order and do not contain duplicates.

## Acceptance

- When every page executor completes its requested page, the report lists each requested page once in `completed`.
- When a `Done` event omits its requested page or reports unrelated page IDs, the report does not claim those pages as completed or updated.
- When a page executor throws a non-cancellation error, that page is incomplete and the next requested page still executes.
- When a page executor reports a rate limit, no later page executes and all remaining pages are listed as incomplete.
- When the coroutine is cancelled, cancellation propagates to the caller.

## Verification

- Unit tests cover scope filtering, missing current-page completion, thrown failures, rate-limit stop behavior, cancellation, and invalid page lists.
