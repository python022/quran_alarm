import Combine
import SwiftUI

@main
public struct QuranAlarmApp: App {
    @StateObject private var alarmViewModel: AlarmViewModel
    @StateObject private var sessionCoordinator: AlarmSessionCoordinator

    private let schedulerForLaunch: IOSAlarmSchedulerImpl?
    private let isUITestMode: Bool

    public init() {
        QuranTypography.registerFontsIfNeeded()

        let args = ProcessInfo.processInfo.arguments
        let uiTestMode = args.contains("-UITestMode")
        let resetState = args.contains("-UITestResetState")

        let repo = AlarmRepositoryImpl()
        let sessionStore = AlarmSessionStore()
        if resetState {
            repo.clearAll()
            sessionStore.clearSession()
        }

        let iosScheduler = IOSAlarmSchedulerImpl(isUITestMode: uiTestMode)
        let activeScheduler: AlarmScheduler = uiTestMode ? NoOpAlarmScheduler() : iosScheduler

        if !uiTestMode {
            iosScheduler.registerCategories()
        }

        let vm = AlarmViewModel(repository: repo, scheduler: activeScheduler)
        let coordinator = AlarmSessionCoordinator(
            repository: repo,
            scheduler: activeScheduler,
            sessionStore: sessionStore,
            isUITestMode: uiTestMode
        )
        coordinator.attachAlarmViewModel(vm)

        _alarmViewModel = StateObject(wrappedValue: vm)
        _sessionCoordinator = StateObject(wrappedValue: coordinator)
        self.schedulerForLaunch = uiTestMode ? nil : iosScheduler
        self.isUITestMode = uiTestMode
    }

    public var body: some Scene {
        WindowGroup {
            ZStack {
                AlarmListView(
                    viewModel: alarmViewModel,
                    sessionCoordinator: sessionCoordinator
                )
                .accessibilityHidden(alarmViewModel.uiState.isAddEditDialogOpen || sessionCoordinator.isPresentingAlert)
                .zIndex(0)

                if alarmViewModel.uiState.isAddEditDialogOpen {
                    AddEditAlarmView(
                        alarmToEdit: alarmViewModel.uiState.editingAlarm,
                        existingAlarmIds: Set(alarmViewModel.uiState.alarms.map(\.id)),
                        onDismiss: { alarmViewModel.dismissDialog() },
                        onSave: { saved in alarmViewModel.saveAlarm(saved) },
                        onDelete: { id in alarmViewModel.deleteAlarm(id) },
                        onPreviewAlarm: { vol, vibrate, soundUri in
                            sessionCoordinator.presentPreview(
                                volumeLevel: vol,
                                isVibrate: vibrate,
                                soundUri: soundUri
                            )
                        }
                    )
                    .accessibilityHidden(sessionCoordinator.isPresentingAlert)
                    .zIndex(1)
                }

                if sessionCoordinator.isPresentingAlert,
                   let alertVM = sessionCoordinator.activeAlertViewModel {
                    AlarmAlertView(
                        viewModel: alertVM,
                        onDismissFinished: {
                            sessionCoordinator.finishSession()
                        }
                    )
                    .zIndex(2)
                }
            }
            .environment(\.layoutDirection, .rightToLeft)
            .preferredColorScheme(.dark)
            .onAppear {
                if !isUITestMode {
                    schedulerForLaunch?.requestAuthorizationIfNeeded { _ in
                        alarmViewModel.refreshDeviceReadiness()
                    }
                    sessionCoordinator.restoreActiveSessionIfNeeded()
                }
                alarmViewModel.refreshDeviceReadiness()
            }
            .onReceive(Timer.publish(every: 30, on: .main, in: .common).autoconnect()) { _ in
                alarmViewModel.updateCountdown()
            }
        }
    }
}
