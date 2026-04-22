# Queue Nested Scroll Host Fix Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the full-screen queue page scroll vertically again by ensuring `BottomSheetBehavior` always recognizes the current page's vertical scroll host.

**Architecture:** Keep the existing three-page horizontal swipe model and fix the real integration point: when the page changes, switch nested-scrolling eligibility between `lyrics_view` and `queue_recycler_view`, then request a relayout on the bottom-sheet child so `BottomSheetBehavior` refreshes its cached scrolling child. Implement the page-to-scroll-host mapping in a small shared helper with unit tests, and apply it through `PlayerViewSwipeController` so the main page and playlist detail page stay consistent.

**Tech Stack:** Android Views, BottomSheetBehavior, RecyclerView, NestedScrollView, Kotlin, JUnit4

---

### Task 1: Add a failing test for page-based nested scroll host selection

**Files:**
- Create: `app/src/main/java/com/musicplayer/ui/main/PlayerNestedScrollTargetResolver.kt`
- Create: `app/src/test/java/com/musicplayer/ui/main/PlayerNestedScrollTargetResolverTest.kt`

- [ ] **Step 1: Write the failing test**

Add tests that define the expected nested-scrolling target for `ALBUM_COVER`, `LYRICS`, and `QUEUE`.

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat testDebugUnitTest --tests "com.musicplayer.ui.main.PlayerNestedScrollTargetResolverTest"`
Expected: FAIL because `PlayerNestedScrollTargetResolver` does not exist yet.

- [ ] **Step 3: Write minimal implementation**

Add a pure helper that returns whether lyrics scrolling and queue scrolling should be enabled for each page.

- [ ] **Step 4: Run test to verify it passes**

Run: `gradlew.bat testDebugUnitTest --tests "com.musicplayer.ui.main.PlayerNestedScrollTargetResolverTest"`
Expected: PASS

### Task 2: Revert the previous fixed-header attempt

**Files:**
- Modify: `app/src/main/java/com/musicplayer/ui/main/QueueSectionBinder.kt`
- Modify: `app/src/main/java/com/musicplayer/ui/widget/PlayerPageSwipeLayout.kt`
- Modify: `app/src/main/res/layout/content_player_detail.xml`
- Modify: `app/src/main/res/values/dimens.xml`
- Delete: `app/src/main/java/com/musicplayer/ui/main/QueueScrollOffsetCalculator.kt`
- Delete: `app/src/main/java/com/musicplayer/ui/widget/AbsoluteTouchBounds.kt`
- Delete: `app/src/main/java/com/musicplayer/ui/widget/VerticalGestureRoutingDecider.kt`
- Delete: `app/src/test/java/com/musicplayer/ui/main/QueueScrollOffsetCalculatorTest.kt`
- Delete: `app/src/test/java/com/musicplayer/ui/widget/AbsoluteTouchBoundsTest.kt`
- Delete: `app/src/test/java/com/musicplayer/ui/widget/VerticalGestureRoutingDeciderTest.kt`

- [ ] **Step 1: Remove queue offset and fixed-header-only code**

Restore the queue binder and shared player layout to the baseline before the failed attempt.

- [ ] **Step 2: Remove helper files and tests created for the failed attempt**

Delete the unused queue-offset and touch-boundary helper code together with their tests.

- [ ] **Step 3: Run a quick diff sanity check**

Run: `git diff -- app/src/main/java/com/musicplayer/ui/main/QueueSectionBinder.kt app/src/main/java/com/musicplayer/ui/widget/PlayerPageSwipeLayout.kt app/src/main/res/layout/content_player_detail.xml app/src/main/res/values/dimens.xml`
Expected: Only the new nested-scroll-host fix remains to be implemented.

### Task 3: Apply the shared nested-scroll-host fix

**Files:**
- Modify: `app/src/main/java/com/musicplayer/ui/main/PlayerViewSwipeController.kt`
- Modify: `app/src/main/java/com/musicplayer/ui/main/PlayerNestedScrollTargetResolver.kt`

- [ ] **Step 1: Wire page changes to nested-scrolling state**

On bind and on every page change, enable nested scrolling only on the active vertical host and disable it on the inactive one.

- [ ] **Step 2: Force BottomSheetBehavior to refresh its scrolling child**

After switching the nested-scrolling state, request layout on the bottom-sheet child view so the behavior re-evaluates its scrolling child.

- [ ] **Step 3: Keep queue-entry behavior unchanged**

Preserve the existing queue-page callback so entering the queue page still scrolls to the current song.

### Task 4: Verify project state

**Files:**
- Verify only

- [ ] **Step 1: Run focused unit tests**

Run: `gradlew.bat testDebugUnitTest --tests "com.musicplayer.ui.main.PlayerNestedScrollTargetResolverTest"`
Expected: PASS

- [ ] **Step 2: Run full required unit tests**

Run: `gradlew.bat testDebugUnitTest`
Expected: PASS

- [ ] **Step 3: Run debug build**

Run: `gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Run emulator verification**

Verify:
- 迷你播放栏上滑展开
- 全屏三页左右切换
- 歌词页上下滚动
- 播放队列页上下滚动
- 队列页到边界后由 `BottomSheetBehavior` 接管
- `PlaylistDetailActivity` 中播放器行为一致
