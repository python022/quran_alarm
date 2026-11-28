import XCTest

final class QuranAlarmUITests: XCTestCase {

    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    func testFullAddConfirmPreviewReadAndDeleteAlarmFlow() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-UITestResetState", "-UITestMode"]
        app.launch()

        let emptyListHeader = app.staticTexts["قائمة المنبهات (0)"]
        XCTAssertTrue(emptyListHeader.waitForExistence(timeout: 10), "Alarm list should initialize with 0 alarms")
        XCTAssertTrue(app.staticTexts["منبه القرآن الكريم"].exists)

        // Open Add Alarm screen
        let addFab = app.buttons["add_alarm_fab"]
        XCTAssertTrue(addFab.waitForExistence(timeout: 5))
        addFab.tap()

        XCTAssertTrue(app.staticTexts["ضبط منبه الورد القرآني"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["section_sound"].exists)
        XCTAssertTrue(app.buttons["section_repeat"].exists)
        XCTAssertTrue(app.buttons["section_label"].exists)

        // Launch live alarm preview -> ringing -> Mushaf reading -> complete page 1 -> exit preview
        let previewBtn = app.buttons["preview_alarm_button"]
        XCTAssertTrue(previewBtn.waitForExistence(timeout: 5))
        previewBtn.tap()

        let startReadingBtn = app.buttons["start_reading_wird_button"]
        XCTAssertTrue(startReadingBtn.waitForExistence(timeout: 5))
        startReadingBtn.tap()

        XCTAssertTrue(app.otherElements["mushaf_page_canvas"].waitForExistence(timeout: 5))

        // Page 1 (Al-Fatihah) has 7 readable lines (2..8); advance until dismiss button unlocks
        let nextStepBtn = app.buttons["next_line_or_page_button"]
        let dismissUnlockedBtn = app.buttons["complete_wird_dismiss_button"]
        for _ in 0..<8 {
            if dismissUnlockedBtn.exists { break }
            if nextStepBtn.waitForExistence(timeout: 3), nextStepBtn.isEnabled {
                nextStepBtn.tap()
            }
        }
        XCTAssertTrue(dismissUnlockedBtn.waitForExistence(timeout: 5), "Dismiss CTA must unlock after finishing page 1")

        let exitPreviewBtn = app.buttons["exit_preview_button"]
        XCTAssertTrue(exitPreviewBtn.waitForExistence(timeout: 5))
        exitPreviewBtn.tap()

        // Save alarm via Wird confirmation modal
        let saveBtn = app.buttons["save_alarm_button"]
        XCTAssertTrue(saveBtn.waitForExistence(timeout: 5))
        saveBtn.tap()

        XCTAssertTrue(app.staticTexts["تأكيد حجم الورد قبل الحفظ"].waitForExistence(timeout: 5))
        app.buttons["confirm_save_wird_button"].tap()

        // Verify single alarm card rendered cleanly
        XCTAssertTrue(app.staticTexts["قائمة المنبهات (1)"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["ورد متسلسل"].exists)

        // Open Edit screen by tapping the saved alarm's Surah summary badge
        let cardSummary = app.staticTexts.matching(NSPredicate(format: "label CONTAINS 'سورة الفاتحة'")).firstMatch
        XCTAssertTrue(cardSummary.waitForExistence(timeout: 5))
        cardSummary.tap()

        XCTAssertTrue(app.staticTexts["تعديل منبه الورد القرآني"].waitForExistence(timeout: 5))

        // Scroll to delete button and confirm deletion
        let deleteBtn = app.buttons["delete_alarm_button"]
        scrollToElement(deleteBtn, in: app)
        XCTAssertTrue(deleteBtn.waitForExistence(timeout: 5))
        deleteBtn.tap()

        XCTAssertTrue(app.staticTexts["تأكيد حذف المنبه"].waitForExistence(timeout: 5))
        app.buttons["confirm_delete_alarm_button"].tap()

        XCTAssertTrue(emptyListHeader.waitForExistence(timeout: 5), "Alarm list should return to 0 after deletion")
    }

    private func scrollToElement(_ element: XCUIElement, in app: XCUIApplication, maxSwipes: Int = 6) {
        let scrollView = app.scrollViews.firstMatch
        for _ in 0..<maxSwipes {
            if element.exists && element.isHittable {
                return
            }
            if scrollView.exists {
                scrollView.swipeUp()
            } else {
                app.swipeUp()
            }
        }
    }
}
