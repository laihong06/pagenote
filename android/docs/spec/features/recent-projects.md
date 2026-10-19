# Android Recent Projects

## Scope

Enable the Android `File > Recent documents` command using a local list of the most recently opened Pagenote projects. Desktop fills this menu from its app-managed document library; Android cannot enumerate arbitrary user folders through SAF, so it records only projects the user has successfully opened. This keeps the same quick-reopen intent without assuming filesystem paths or scanning external storage.

Store only project identity/display metadata and the SAF tree URI needed to reopen the project. Do not copy project contents into app storage, change the `.pagenote` format, or revoke permissions when an item is removed from the list.

## Behavior

- After a project has fully opened, add or move its `(tree URI, root document ID)` entry to the top of the recent list. Save the project ID, manifest name, and last-opened timestamp. Keep at most 12 entries.
- Persist the list in the app's private DataStore so it survives Activity/process restarts. Record an entry only when the app has a persistable read grant for its tree URI.
- The Recent documents dialog lists entries newest-first. Selecting an entry uses the existing project-open pipeline and saved SAF grant; it does not launch another app or system picker.
- Before switching from a dirty current note, save the current page using the existing revision-checked writer. If saving fails or is cancelled, keep the current project open and do not switch.
- If a stored SAF permission has been revoked or its document is unavailable, report the open failure and keep the recent entry available for removal or retry after reopening through the project picker.
- Removing a recent item deletes only its DataStore entry. It does not delete the project or release its SAF grant.
- With no entries, show an explicit empty state and a button that opens the existing SAF project picker.
- The feature does not enumerate arbitrary SAF directories, import documents, or change the current startup session-restoration behavior.

## Acceptance Criteria (EARS)

- When a project opens successfully with a persisted read grant, Android shall add it to the top of the recent list and persist its tree URI, root document ID, project ID, display name, and last-opened time.
- When the same project is opened again, Android shall update its recent position rather than create a duplicate; the list shall contain no more than 12 entries.
- When the user chooses a recent entry, Android shall reopen it through the existing SAF project loader and preserve the project format and normal writable/read-only detection.
- While the current page note is dirty, when the user chooses another recent project, Android shall save that page first and switch only after save succeeds.
- When the current-page save fails or is cancelled, Android shall leave the current project and recent selection unchanged.
- When the user removes an entry, Android shall remove only its recent metadata and shall not delete project files or release the SAF permission.
- When the saved SAF grant is missing or opening the URI fails, Android shall report a recoverable error and shall not claim that the project opened.
- When no recent projects exist, Android shall show an empty state and retain a route to the existing SAF project picker.

## Verification

- Focused JVM tests cover ordering, deduplication, the 12-entry bound, malformed stored data, removal, and DataStore persistence across store instances.
- One Android 14/API 34 tablet AVD Activity test covers the empty state, recent-item reopening through a SAF test provider, dirty-note save-before-switch, and removal behavior without opening another app.
- Build the Android debug app after the focused checks. Xiaomi Pad 5 Pro acceptance remains in parent task 12; the AVD is not hardware evidence.
