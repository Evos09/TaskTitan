import CryptoKit
import Foundation
import Security

/// Stores the whole app state as JSON sealed with AES-256-GCM. The key lives in the
/// Keychain (this device only) and the file uses complete file protection, so data
/// at rest is never stored in plain text.
nonisolated struct SecureStore: Sendable {
    private static let keyAccount = "task_titan_data_key"
    private static let service = "app.rork.tasktitan"

    private var fileURL: URL {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
            ?? URL(fileURLWithPath: NSTemporaryDirectory())
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir.appendingPathComponent("tasktitan.sealed")
    }

    private func keyQuery() -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: Self.service,
            kSecAttrAccount as String: Self.keyAccount,
        ]
    }

    private func key() throws -> SymmetricKey {
        var query = keyQuery()
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        var item: CFTypeRef?
        if SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess, let data = item as? Data {
            return SymmetricKey(data: data)
        }
        let key = SymmetricKey(size: .bits256)
        var add = keyQuery()
        add[kSecValueData as String] = key.withUnsafeBytes { Data($0) }
        add[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        let status = SecItemAdd(add as CFDictionary, nil)
        guard status == errSecSuccess else { throw StoreError.keychain(status) }
        return key
    }

    enum StoreError: Error { case keychain(OSStatus) }

    func load() -> AppData? {
        guard let sealed = try? Data(contentsOf: fileURL) else { return nil }
        do {
            let box = try AES.GCM.SealedBox(combined: sealed)
            let plain = try AES.GCM.open(box, using: key())
            return try JSONDecoder().decode(AppData.self, from: plain)
        } catch {
            print("SecureStore: could not restore saved data (\(type(of: error)))")
            return nil
        }
    }

    func save(_ data: AppData) {
        do {
            let json = try JSONEncoder().encode(data)
            let sealed = try AES.GCM.seal(json, using: key())
            guard let combined = sealed.combined else { return }
            try combined.write(to: fileURL, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
        } catch {
            print("SecureStore: could not save data (\(type(of: error)))")
        }
    }

    /// Readable export the user can share with themselves.
    func export(_ data: AppData) -> String {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        guard let json = try? encoder.encode(data) else { return "{}" }
        return String(data: json, encoding: .utf8) ?? "{}"
    }

    func wipe() {
        try? FileManager.default.removeItem(at: fileURL)
        SecItemDelete(keyQuery() as CFDictionary)
    }
}
