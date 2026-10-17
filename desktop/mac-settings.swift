import AppKit

// A native accessory window owned by Electron. The private stdio channel is
// the only bridge: no local-server credentials, web views or JavaScript.
final class SettingsApp: NSObject, NSApplicationDelegate, NSWindowDelegate, NSToolbarDelegate, NSTextViewDelegate {
    var window: NSWindow!
    var toolbar: NSToolbar!
    var values: [String: Any] = [:]
    var pane = "general"
    let panes = ["general", "appearance", "reading", "ai"]
    let modules = ["system", "entry", "generation", "learning", "page-tutor", "output", "tag-edit", "learner", "profile"]
    var controls: [String: NSControl] = [:]
    var callbacks: [String: ([String: Any]) -> Void] = [:]
    var sequence = 0
    var status: NSTextField?
    var editor: NSTextView?
    var module = "system"
    struct Draft { var text: String; var saved: String; var defaultText: String; var revision: String }
    var drafts: [String: Draft] = [:]
    var closeRequest: String?
    var savingOnClose = false
    var waitingToClose = false

    var english: Bool { values["language"] as? String == "en-US" }
    func tr(_ zh: String, _ en: String) -> String { english ? en : zh }
    func title(_ id: String) -> String {
        switch id {
        case "appearance": return tr("外观", "Appearance")
        case "reading": return tr("阅读", "Reading")
        case "ai": return "AI"
        default: return tr("通用", "General")
        }
    }
    func moduleTitle(_ id: String) -> String {
        switch id {
        case "entry": return tr("入口与路由", "Entry and routing")
        case "generation": return tr("生成策略", "Generation strategy")
        case "profile": return tr("用户画像", "Learner preferences")
        case "learning": return tr("学习讲解", "Learning")
        case "page-tutor": return tr("逐页精讲", "Page tutor")
        case "output": return tr("输出格式", "Output format")
        case "tag-edit": return tr("AI Tag 修改", "AI Tag editing")
        case "learner": return tr("学习背景", "Learning background")
        default: return tr("默认系统提示词", "Default system prompt")
        }
    }
    func emit(_ packet: [String: Any]) {
        guard let data = try? JSONSerialization.data(withJSONObject: packet),
              var line = String(data: data, encoding: .utf8) else { return }
        line += "\n"
        FileHandle.standardOutput.write(Data(line.utf8))
    }
    func request(_ action: String, _ fields: [String: Any] = [:], completion: @escaping ([String: Any]) -> Void) {
        sequence += 1
        let id = "appkit-\(sequence)"
        callbacks[id] = completion
        updateEnabled()
        var packet = fields
        packet["action"] = action
        packet["requestId"] = id
        emit(packet)
    }
    func applicationDidFinishLaunching(_ notification: Notification) {
        NSApp.setActivationPolicy(.accessory)
        installMenu()
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            while let line = readLine() {
                guard let data = line.data(using: .utf8),
                      let packet = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { continue }
                DispatchQueue.main.async { self?.receive(packet) }
            }
            // EOF means the parent has exited. Never leave an orphan window.
            DispatchQueue.main.async { NSApp.terminate(nil) }
        }
    }
    func installMenu() {
        let menu = NSMenu()
        let appItem = NSMenuItem()
        let appMenu = NSMenu()
        let quit = NSMenuItem(title: tr("退出 pagenote", "Quit pagenote"), action: #selector(quitApp), keyEquivalent: "q")
        quit.target = self
        appMenu.addItem(quit)
        appItem.submenu = appMenu
        menu.addItem(appItem)
        let editItem = NSMenuItem(title: tr("编辑", "Edit"), action: nil, keyEquivalent: "")
        let editMenu = NSMenu(title: tr("编辑", "Edit"))
        for (name, selector, key) in [(tr("撤销", "Undo"), "undo:", "z"), (tr("重做", "Redo"), "redo:", "Z"), (tr("剪切", "Cut"), "cut:", "x"), (tr("复制", "Copy"), "copy:", "c"), (tr("粘贴", "Paste"), "paste:", "v"), (tr("全选", "Select All"), "selectAll:", "a")] {
            editMenu.addItem(NSMenuItem(title: name, action: Selector(selector), keyEquivalent: key))
        }
        editItem.submenu = editMenu
        menu.addItem(editItem)
        let windowItem = NSMenuItem(title: tr("窗口", "Window"), action: nil, keyEquivalent: "")
        let windowMenu = NSMenu(title: tr("窗口", "Window"))
        windowMenu.addItem(NSMenuItem(title: tr("关闭", "Close"), action: #selector(NSWindow.performClose(_:)), keyEquivalent: "w"))
        let settings = NSMenuItem(title: tr("设置…", "Settings…"), action: #selector(showAgain), keyEquivalent: ",")
        settings.target = self
        windowMenu.addItem(settings)
        windowItem.submenu = windowMenu
        menu.addItem(windowItem)
        NSApp.mainMenu = menu
    }
    @objc func quitApp() { emit(["event": "quit"]) }
    @objc func showAgain() { window?.makeKeyAndOrderFront(nil) }

    func receive(_ packet: [String: Any]) {
        if let reply = packet["replyTo"] as? String {
            let completion = callbacks.removeValue(forKey: reply)
            if let state = packet["state"] as? [String: Any] { apply(state) }
            updateEnabled()
            completion?(packet)
            updateEnabled()
            if callbacks.isEmpty && waitingToClose && !savingOnClose {
                waitingToClose = false
                window.performClose(nil)
            }
            return
        }
        if let state = packet["state"] as? [String: Any] { apply(state) }
        switch packet["command"] as? String {
        case "show":
            let next = packet["pane"] as? String ?? "general"
            guard panes.contains(next) else { return }
            if window == nil { createWindow() }
            showPane(next)
            window.makeKeyAndOrderFront(nil)
            NSApp.activate(ignoringOtherApps: true)
            emit(["event": "shown", "requestId": packet["requestId"] ?? "", "windowNumber": window.windowNumber, "pane": pane])
        case "close":
            closeRequest = packet["requestId"] as? String
            window?.performClose(nil)
        default: break
        }
    }
    func apply(_ state: [String: Any]) {
        let initial = values.isEmpty
        let previousLanguage = english
        values = state
        if initial || previousLanguage != english { installMenu() }
        switch values["theme"] as? String {
        case "light": NSApp.appearance = NSAppearance(named: .aqua)
        case "dark": NSApp.appearance = NSAppearance(named: .darkAqua)
        default: NSApp.appearance = nil
        }
        if window != nil && previousLanguage != english {
            for item in toolbar.items { item.label = title(item.itemIdentifier.rawValue) }
            showPane(pane)
        } else {
            for (key, control) in controls {
                if let popup = control as? NSPopUpButton, let value = values[key] as? String {
                    if let item = popup.itemArray.first(where: { $0.representedObject as? String == value }) { popup.select(item) }
                } else if let toggle = control as? NSSwitch { toggle.state = values[key] as? Bool == true ? .on : .off }
            }
        }
    }
    func createWindow() {
        window = NSWindow(contentRect: NSRect(x: 0, y: 0, width: 680, height: 420), styleMask: [.titled, .closable], backing: .buffered, defer: false)
        window.isReleasedWhenClosed = false
        window.delegate = self
        window.identifier = NSUserInterfaceItemIdentifier("pagenote-native-settings")
        window.standardWindowButton(.miniaturizeButton)?.isEnabled = false
        window.standardWindowButton(.zoomButton)?.isEnabled = false
        toolbar = NSToolbar(identifier: "pagenote-settings-toolbar")
        toolbar.delegate = self
        toolbar.displayMode = .iconAndLabel
        toolbar.allowsUserCustomization = false
        toolbar.autosavesConfiguration = false
        window.toolbar = toolbar
        window.toolbarStyle = .preference
        window.center()
    }
    func toolbarAllowedItemIdentifiers(_ toolbar: NSToolbar) -> [NSToolbarItem.Identifier] { panes.map { .init($0) } }
    func toolbarDefaultItemIdentifiers(_ toolbar: NSToolbar) -> [NSToolbarItem.Identifier] { panes.map { .init($0) } }
    func toolbarSelectableItemIdentifiers(_ toolbar: NSToolbar) -> [NSToolbarItem.Identifier] { panes.map { .init($0) } }
    func toolbar(_ toolbar: NSToolbar, itemForItemIdentifier id: NSToolbarItem.Identifier, willBeInsertedIntoToolbar flag: Bool) -> NSToolbarItem? {
        let item = NSToolbarItem(itemIdentifier: id)
        item.label = title(id.rawValue)
        item.paletteLabel = item.label
        let symbol = ["general": "gearshape", "appearance": "paintpalette", "reading": "book", "ai": "sparkles"][id.rawValue] ?? "gearshape"
        item.image = NSImage(systemSymbolName: symbol, accessibilityDescription: item.label)
        item.target = self
        item.action = #selector(selectPane(_:))
        return item
    }
    @objc func selectPane(_ sender: NSToolbarItem) { showPane(sender.itemIdentifier.rawValue) }
    func label(_ text: String, secondary: Bool = false) -> NSTextField {
        let field = NSTextField(wrappingLabelWithString: text)
        field.font = .systemFont(ofSize: secondary ? 12 : 13)
        field.textColor = secondary ? .secondaryLabelColor : .labelColor
        field.maximumNumberOfLines = 0
        return field
    }
    func stack(_ orientation: NSUserInterfaceLayoutOrientation, spacing: CGFloat = 16) -> NSStackView {
        let view = NSStackView()
        view.orientation = orientation
        view.spacing = spacing
        view.alignment = orientation == .vertical ? .leading : .centerY
        view.distribution = .fill
        return view
    }
    func add(_ view: NSView, to root: NSStackView) {
        root.addArrangedSubview(view)
        view.widthAnchor.constraint(equalTo: root.widthAnchor).isActive = true
    }
    func row(_ heading: String, _ detail: String, control: NSControl, root: NSStackView) {
        let row = stack(.horizontal, spacing: 24)
        let copy = stack(.vertical, spacing: 5)
        copy.addArrangedSubview(label(heading))
        if !detail.isEmpty { add(label(detail, secondary: true), to: copy) }
        row.addArrangedSubview(copy)
        row.addArrangedSubview(control)
        control.setContentHuggingPriority(.required, for: .horizontal)
        control.setContentCompressionResistancePriority(.required, for: .horizontal)
        copy.setContentHuggingPriority(.defaultLow, for: .horizontal)
        control.setAccessibilityLabel(heading)
        add(row, to: root)
    }
    func popup(_ key: String, choices: [(String, String)]) -> NSPopUpButton {
        let popup = NSPopUpButton(frame: .zero, pullsDown: false)
        for (value, name) in choices { popup.addItem(withTitle: name); popup.lastItem?.representedObject = value }
        popup.identifier = .init(key)
        popup.setAccessibilityIdentifier(key)
        popup.target = self
        popup.action = #selector(changed(_:))
        popup.widthAnchor.constraint(equalToConstant: 160).isActive = true
        if let value = values[key] as? String, let item = popup.itemArray.first(where: { $0.representedObject as? String == value }) { popup.select(item) }
        controls[key] = popup
        return popup
    }
    func toggle(_ key: String) -> NSSwitch {
        let toggle = NSSwitch()
        toggle.identifier = .init(key)
        toggle.setAccessibilityIdentifier(key)
        toggle.state = values[key] as? Bool == true ? .on : .off
        toggle.target = self
        toggle.action = #selector(changed(_:))
        controls[key] = toggle
        return toggle
    }
    @objc func changed(_ sender: NSControl) {
        guard let key = sender.identifier?.rawValue else { return }
        let value: Any
        if let popup = sender as? NSPopUpButton { value = popup.selectedItem?.representedObject ?? "" }
        else if let toggle = sender as? NSSwitch { value = toggle.state == .on }
        else { return }
        setStatus(tr("正在保存…", "Saving…"))
        request("set", ["key": key, "value": value]) { [weak self] reply in
            guard let self = self else { return }
            if let error = reply["error"] as? String { self.setStatus(error, error: true) }
            else { self.setStatus(self.tr("更改已保存", "Changes saved")) }
        }
    }
    func showPane(_ next: String) {
        guard panes.contains(next) else { return }
        pane = next
        editor = nil
        controls.removeAll()
        window.title = "pagenote — " + title(pane)
        toolbar.selectedItemIdentifier = .init(pane)
        let root = stack(.vertical, spacing: 24)
        root.translatesAutoresizingMaskIntoConstraints = false
        let content = NSView()
        content.addSubview(root)
        window.contentView = content
        NSLayoutConstraint.activate([
            root.leadingAnchor.constraint(equalTo: content.leadingAnchor, constant: 32),
            root.trailingAnchor.constraint(equalTo: content.trailingAnchor, constant: -32),
            root.topAnchor.constraint(equalTo: content.topAnchor, constant: 28),
            root.bottomAnchor.constraint(lessThanOrEqualTo: content.bottomAnchor, constant: -22),
        ])
        switch pane {
        case "general":
            row(tr("语言", "Language"), tr("应用界面和菜单的显示语言。", "The language used for the interface and menus."), control: popup("language", choices: [("zh-CN", "简体中文"), ("en-US", "English")]), root: root)
            add(label(tr("更改即时生效，文档与笔记内容保持原样。", "Changes take effect immediately. Document and note content is preserved."), secondary: true), to: root)
        case "appearance":
            row(tr("外观", "Appearance"), tr("跟随系统，或为 pagenote 单独选择。", "Follow your Mac or choose an appearance for pagenote."), control: popup("theme", choices: [("system", tr("跟随系统", "System")), ("light", tr("浅色", "Light")), ("dark", tr("深色", "Dark"))]), root: root)
            row(tr("Markdown 主题", "Markdown theme"), tr("纸页主题使用衬线字体和暖纸配色。", "Paper uses serif type and warm paper colors."), control: popup("markdownTheme", choices: [("classic", tr("经典", "Classic")), ("paper", tr("纸页 · 衬线", "Paper · Serif"))]), root: root)
        case "reading":
            row(tr("平滑换页", "Smooth page transitions"), tr("翻阅原文时，右侧讲解轻柔淡入。", "Gently fade in the notes when turning a source page."), control: toggle("noteAnimation"), root: root)
            row(tr("编辑时自动展开", "Expand while editing"), tr("编辑原文或笔记时展开当前区域；可随时返回双栏。", "Expand the active editor. You can return to the split view at any time."), control: toggle("expandEditor"), root: root)
        case "ai": buildAI(root)
        default: break
        }
        status = label("", secondary: true)
        status?.setAccessibilityIdentifier("settingsStatus")
        add(status!, to: root)
        if pane != "ai" {
            let version = values["version"] as? String ?? ""
            add(label("pagenote " + version, secondary: true), to: root)
        }
        let size = NSSize(width: pane == "ai" ? 760 : 680, height: pane == "ai" ? 570 : (pane == "general" ? 250 : 300))
        let old = window.frame
        let full = window.frameRect(forContentRect: NSRect(origin: .zero, size: size))
        window.setFrame(NSRect(x: old.minX, y: old.maxY - full.height, width: full.width, height: full.height), display: true, animate: window.isVisible)
        updateEnabled()
        emit(["event": "pane", "pane": pane])
    }
    func setStatus(_ message: String, error: Bool = false) {
        status?.stringValue = message
        status?.textColor = error ? .systemRed : .secondaryLabelColor
    }
    func updateEnabled() {
        let enabled = callbacks.isEmpty && !savingOnClose
        for control in controls.values { control.isEnabled = enabled }
        let loaded = drafts[module] != nil
        editor?.isEditable = enabled && loaded
        controls["savePrompt"]?.isEnabled = enabled && loaded
        controls["resetPrompt"]?.isEnabled = enabled && loaded
        controls["retryPrompt"]?.isHidden = loaded
        // Keep pane navigation available; drafts are retained independently.
    }
    func buildAI(_ root: NSStackView) {
        let select = NSPopUpButton(frame: .zero, pullsDown: false)
        for id in modules { select.addItem(withTitle: moduleTitle(id)); select.lastItem?.representedObject = id }
        select.selectItem(at: modules.firstIndex(of: module) ?? 0)
        select.target = self
        select.action = #selector(selectModule(_:))
        select.setAccessibilityIdentifier("promptModule")
        controls["promptModule"] = select
        row(tr("提示词模块", "Prompt module"), "", control: select, root: root)
        add(label(tr("仅保存当前模块。系统提示词保存后需重新连接 AI 客户端，其他模块下次读取时生效。", "Save the current module. Reconnect your AI client after changing the system prompt; other modules apply on the next read."), secondary: true), to: root)
        let scroll = NSScrollView()
        scroll.hasVerticalScroller = true
        scroll.borderType = .bezelBorder
        scroll.heightAnchor.constraint(equalToConstant: 280).isActive = true
        let text = NSTextView(frame: NSRect(x: 0, y: 0, width: 690, height: 280))
        text.isRichText = false
        text.allowsUndo = true
        text.isAutomaticQuoteSubstitutionEnabled = false
        text.isAutomaticDashSubstitutionEnabled = false
        text.isAutomaticTextReplacementEnabled = false
        text.isContinuousSpellCheckingEnabled = false
        text.font = .monospacedSystemFont(ofSize: 13, weight: .regular)
        text.textContainerInset = NSSize(width: 12, height: 12)
        text.isVerticallyResizable = true
        text.isHorizontallyResizable = false
        text.autoresizingMask = [.width]
        text.textContainer?.widthTracksTextView = true
        text.delegate = self
        text.setAccessibilityLabel(tr("提示词内容", "Prompt content"))
        text.setAccessibilityIdentifier("promptContent")
        scroll.documentView = text
        editor = text
        add(scroll, to: root)
        let actions = stack(.horizontal)
        let reset = NSButton(title: tr("恢复默认", "Restore Defaults"), target: self, action: #selector(resetPrompt))
        reset.bezelStyle = .rounded
        reset.setAccessibilityIdentifier("resetPrompt")
        controls["resetPrompt"] = reset
        actions.addArrangedSubview(reset)
        let retry = NSButton(title: tr("重新加载", "Retry"), target: self, action: #selector(retryPrompt))
        retry.bezelStyle = .rounded
        retry.setAccessibilityIdentifier("retryPrompt")
        controls["retryPrompt"] = retry
        actions.addArrangedSubview(retry)
        let spacer = NSView()
        spacer.setContentHuggingPriority(.defaultLow, for: .horizontal)
        actions.addArrangedSubview(spacer)
        let save = NSButton(title: tr("保存", "Save"), target: self, action: #selector(savePrompt))
        save.bezelStyle = .rounded
        save.keyEquivalent = "s"
        save.keyEquivalentModifierMask = .command
        save.setAccessibilityIdentifier("savePrompt")
        controls["savePrompt"] = save
        actions.addArrangedSubview(save)
        add(actions, to: root)
        loadPrompt()
    }
    @objc func selectModule(_ sender: NSPopUpButton) {
        module = sender.selectedItem?.representedObject as? String ?? "system"
        editor?.undoManager?.removeAllActions()
        loadPrompt()
    }
    @objc func retryPrompt() { loadPrompt() }
    func loadPrompt() {
        if let draft = drafts[module] { editor?.string = draft.text; return }
        editor?.string = ""
        let selected = module
        request("read-prompt", ["module": selected]) { [weak self] reply in
            guard let self = self else { return }
            if let error = reply["error"] as? String { self.setStatus(error, error: true); return }
            guard let prompt = reply["prompt"] as? [String: Any], let text = prompt["text"] as? String, let defaults = prompt["defaultText"] as? String else { return }
            self.drafts[selected] = Draft(text: text, saved: text, defaultText: defaults, revision: prompt["revision"] as? String ?? "")
            if self.module == selected {
                self.editor?.string = text
                self.setStatus("")
            }
        }
    }
    func textDidChange(_ notification: Notification) {
        guard var draft = drafts[module], let editor = editor else { return }
        draft.text = editor.string
        drafts[module] = draft
        setStatus(draft.text == draft.saved ? "" : tr("有未保存的更改", "Unsaved changes"))
    }
    @objc func resetPrompt() {
        guard var draft = drafts[module] else { return }
        draft.text = draft.defaultText
        drafts[module] = draft
        editor?.string = draft.text
        setStatus(tr("已恢复默认内容，保存后生效", "Default restored. Save to apply."))
    }
    @objc func savePrompt() { save(module) { _ in } }
    func save(_ selected: String, completion: @escaping (Bool) -> Void) {
        guard let draft = drafts[selected] else { completion(false); return }
        request("save-prompt", ["module": selected, "text": draft.text, "expectedRevision": draft.revision]) { [weak self] reply in
            guard let self = self else { return }
            if let error = reply["error"] as? String { self.setStatus(error, error: true); completion(false); return }
            self.drafts[selected]?.saved = draft.text
            if let prompt = reply["prompt"] as? [String: Any], let revision = prompt["revision"] as? String { self.drafts[selected]?.revision = revision }
            self.setStatus(self.tr("更改已保存", "Changes saved"))
            completion(true)
        }
    }
    func windowShouldClose(_ sender: NSWindow) -> Bool {
        if !callbacks.isEmpty || savingOnClose {
            setStatus(tr("正在处理更改，请稍候。", "Please wait while changes are processed."))
            waitingToClose = true
            return false
        }
        let dirty = modules.filter { drafts[$0].map { $0.text != $0.saved } ?? false }
        if dirty.isEmpty { return true }
        let alert = NSAlert()
        alert.messageText = tr("要保存提示词更改吗？", "Save your prompt changes?")
        alert.informativeText = tr("未保存的模块：", "Unsaved modules: ") + dirty.map { moduleTitle($0) }.joined(separator: "、")
        alert.addButton(withTitle: tr("保存更改", "Save Changes"))
        alert.addButton(withTitle: tr("不保存", "Discard"))
        alert.addButton(withTitle: tr("取消", "Cancel"))
        let response = alert.runModal()
        if response == .alertSecondButtonReturn { return true }
        if response == .alertThirdButtonReturn { cancelClose(); return false }
        savingOnClose = true
        updateEnabled()
        saveBeforeClose(dirty)
        return false
    }
    func saveBeforeClose(_ remaining: [String]) {
        guard let next = remaining.first else {
            savingOnClose = false
            window.performClose(nil)
            return
        }
        save(next) { [weak self] success in
            guard let self = self else { return }
            if success { self.saveBeforeClose(Array(remaining.dropFirst())) }
            else { self.savingOnClose = false; self.updateEnabled(); self.cancelClose() }
        }
    }
    func cancelClose() {
        emit(["event": "close-cancelled", "requestId": closeRequest ?? ""])
        closeRequest = nil
    }
    func windowWillClose(_ notification: Notification) {
        emit(["event": "closed"])
        NSApp.terminate(nil)
    }
}

let application = NSApplication.shared
let delegate = SettingsApp()
application.delegate = delegate
application.run()
