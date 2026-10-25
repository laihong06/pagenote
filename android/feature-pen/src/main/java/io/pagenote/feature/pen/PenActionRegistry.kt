package io.pagenote.feature.pen

/**
 * Registry of known action ids (port batch 1). [PenActionStack.execute]
 * rejects unregistered actions, so every action implementation must register
 * its id next to its definition — the registry is the catalog of all undoable
 * operations in the process.
 */
object PenActionRegistry {
  private val descriptions = linkedMapOf<String, String>()

  /** Registers [actionId]; re-registering the same id+description is a no-op. */
  fun register(actionId: String, description: String) {
    require(actionId.isNotBlank()) { "blank action id" }
    require(description.isNotBlank()) { "blank action description for $actionId" }
    val existing = descriptions[actionId]
    require(existing == null || existing == description) {
      "action id registered with a different description: $actionId"
    }
    descriptions[actionId] = description
  }

  fun isRegistered(actionId: String): Boolean = descriptions.containsKey(actionId)

  fun describe(actionId: String): String? = descriptions[actionId]

  fun knownActionIds(): Set<String> = descriptions.keys.toSet()

  /** Test/registry-reset hook. Production code never unregisters. */
  internal fun clearForTest() = descriptions.clear()
}
