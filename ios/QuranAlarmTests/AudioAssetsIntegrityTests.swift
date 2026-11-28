import AVFoundation
import XCTest
@testable import QuranAlarm

final class AudioAssetsIntegrityTests: XCTestCase {

    func testAllBundledM4AAssetsPlayableAndNetworkSchemesRejected() throws {
        let presets: [String?] = [
            nil,
            AlarmSoundPresets.uriAdhanFajr,
            AlarmSoundPresets.uriEidTakbeer,
            AlarmSoundPresets.uriCalmTone,
            AlarmSoundPresets.uriQuranAlert
        ]

        for uri in presets {
            let url = AlarmSoundPresets.resolveAudioURL(for: uri, bundle: .main)
            XCTAssertNotNil(url, "Missing bundled audio for preset \(uri ?? "default")")
            if let url {
                let player = try AVAudioPlayer(contentsOf: url)
                XCTAssertGreaterThan(player.duration, 1.0, "Bundled audio duration invalid for \(url.lastPathComponent)")
            }
        }

        XCTAssertNil(AlarmSoundPresets.resolveAudioURL(for: "https://example.com/alarm.mp3", bundle: .main))
        XCTAssertNil(AlarmSoundPresets.resolveAudioURL(for: "http://example.com/alarm.mp3", bundle: .main))
        XCTAssertNil(AlarmSoundPresets.resolveAudioURL(for: "ftp://example.com/alarm.mp3", bundle: .main))
    }

    func testAllNotificationCAFTonesExistAndAreUnder30Seconds() throws {
        let presets = [
            AlarmSoundPresets.uriAdhanFajr,
            AlarmSoundPresets.uriEidTakbeer,
            AlarmSoundPresets.uriCalmTone,
            AlarmSoundPresets.uriQuranAlert
        ]

        for uri in presets {
            let cafName = try XCTUnwrap(AlarmSoundPresets.notificationSoundFileName(for: uri))
            let baseName = cafName.replacingOccurrences(of: ".caf", with: "")
            let url = Bundle.main.url(forResource: baseName, withExtension: "caf")
            XCTAssertNotNil(url, "Missing notification tone \(cafName) in bundle")
            if let url {
                let player = try AVAudioPlayer(contentsOf: url)
                XCTAssertGreaterThan(player.duration, 1.0)
                XCTAssertLessThan(player.duration, 30.0, "\(cafName) must be under Apple's 30s notification limit")
            }
        }
    }
}
