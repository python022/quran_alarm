import AVFoundation
import AudioToolbox
import Combine
import Foundation

public enum AudioPlaybackState: Equatable {
    case idle
    case blaring
    case stopped
}

/// Smart audio & vibration manager for Quran Alarm on iOS.
/// Uses AVAudioSession (.playback) so in-app ringing overrides the iPhone hardware mute switch.
public final class SmartAlarmAudioManager: NSObject, ObservableObject, AVAudioPlayerDelegate {
    @Published public private(set) var playbackState: AudioPlaybackState = .idle

    private var audioPlayer: AVAudioPlayer?
    private var vibrationTimer: Timer?
    private var previewTimerWorkItem: DispatchWorkItem?
    private var previewCompletion: (() -> Void)?
    private var playbackGeneration: UInt64 = 0
    private var targetVolumePercent: Int = 100

    public override init() {
        super.init()
    }

    deinit {
        stopAlarm()
    }

    public func startAlarm(
        soundUriString: String?,
        volumeLevel: Int = 100,
        isVibrationEnabled: Bool = true
    ) {
        stopAlarm()
        playbackGeneration &+= 1
        targetVolumePercent = min(max(volumeLevel, 1), 100)

        configurePlaybackAudioSession()

        let url = AlarmSoundPresets.resolveAudioURL(for: soundUriString)
            ?? AlarmSoundPresets.resolveAudioURL(for: AlarmSoundPresets.uriQuranAlert)

        if let audioURL = url, let player = try? AVAudioPlayer(contentsOf: audioURL) {
            player.delegate = self
            player.numberOfLoops = -1
            player.volume = Float(targetVolumePercent) / 100.0
            player.prepareToPlay()
            if player.play() {
                audioPlayer = player
                playbackState = .blaring
            } else {
                playFallbackSystemTone()
            }
        } else {
            playFallbackSystemTone()
        }

        if isVibrationEnabled {
            startVibrationLoop()
        }
    }

    public func updateVolume(_ volumeLevel: Int) {
        targetVolumePercent = min(max(volumeLevel, 1), 100)
        audioPlayer?.volume = Float(targetVolumePercent) / 100.0
    }

    public func previewSound(
        soundUriString: String?,
        volumeLevel: Int = 80,
        durationMs: Int64? = nil,
        onError: @escaping () -> Void = {},
        onComplete: @escaping () -> Void = {}
    ) {
        stopAlarm()
        playbackGeneration &+= 1
        let generation = playbackGeneration
        targetVolumePercent = min(max(volumeLevel, 1), 100)

        configurePlaybackAudioSession()

        guard let audioURL = AlarmSoundPresets.resolveAudioURL(for: soundUriString)
            ?? AlarmSoundPresets.resolveAudioURL(for: AlarmSoundPresets.uriQuranAlert) else {
            onError()
            onComplete()
            return
        }

        do {
            let player = try AVAudioPlayer(contentsOf: audioURL)
            player.delegate = self
            player.numberOfLoops = 0
            player.volume = Float(targetVolumePercent) / 100.0
            player.prepareToPlay()

            guard player.play() else {
                stopAlarm()
                onError()
                onComplete()
                return
            }

            audioPlayer = player
            previewCompletion = onComplete
            playbackState = .blaring

            if let durationMs, durationMs > 0 {
                let workItem = DispatchWorkItem { [weak self] in
                    guard let self, self.playbackGeneration == generation else { return }
                    let completion = self.previewCompletion
                    self.stopAlarm()
                    completion?()
                }
                previewTimerWorkItem = workItem
                DispatchQueue.main.asyncAfter(
                    deadline: .now() + Double(durationMs) / 1000.0,
                    execute: workItem
                )
            }
        } catch {
            stopAlarm()
            onError()
            onComplete()
        }
    }

    public func stopAlarm() {
        playbackGeneration &+= 1
        previewTimerWorkItem?.cancel()
        previewTimerWorkItem = nil
        previewCompletion = nil

        vibrationTimer?.invalidate()
        vibrationTimer = nil

        if let player = audioPlayer {
            player.stop()
            player.delegate = nil
        }
        audioPlayer = nil

        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        playbackState = .stopped
    }

    public func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        guard player === audioPlayer else { return }
        let completion = previewCompletion
        stopAlarm()
        completion?()
    }

    private func configurePlaybackAudioSession() {
        let session = AVAudioSession.sharedInstance()
        try? session.setCategory(.playback, mode: .default, options: [.duckOthers])
        try? session.setActive(true)
    }

    private func playFallbackSystemTone() {
        AudioServicesPlaySystemSound(1005)
        playbackState = .blaring
    }

    private func startVibrationLoop() {
        vibrationTimer?.invalidate()
        AudioServicesPlaySystemSound(kSystemSoundID_Vibrate)
        let timer = Timer(timeInterval: 1.2, repeats: true) { _ in
            AudioServicesPlaySystemSound(kSystemSoundID_Vibrate)
        }
        RunLoop.main.add(timer, forMode: .common)
        vibrationTimer = timer
    }
}
