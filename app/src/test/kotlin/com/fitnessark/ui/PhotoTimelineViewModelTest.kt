package com.fitnessark.ui

import com.fitnessark.TestSupport
import com.fitnessark.await
import com.fitnessark.data.local.AppDatabase
import com.fitnessark.data.model.PhotoAngle
import com.fitnessark.data.repository.PhotoRepository
import com.fitnessark.ui.photos.ComparisonLayout
import com.fitnessark.ui.photos.PhotoTimelineViewModel
import com.fitnessark.util.ImageCompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PhotoTimelineViewModelTest {

    private lateinit var db: AppDatabase
    private lateinit var vm: PhotoTimelineViewModel

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = TestSupport.inMemoryDb()
        vm = PhotoTimelineViewModel(PhotoRepository(db.photoDao(), ImageCompressor(), TestSupport.context()))
    }

    @After fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test fun comparison_defaults_to_the_front_pose_as_a_slider() = runBlocking {
        val state = vm.uiState.await { !it.isLoading }
        assertEquals(PhotoAngle.FRONT, state.compareAngle)
        assertEquals(ComparisonLayout.SLIDER, state.compareLayout)
    }

    @Test fun pose_and_layout_choices_are_kept_when_comparison_mode_is_toggled() = runBlocking {
        vm.uiState.await { !it.isLoading }

        vm.setCompareAngle(PhotoAngle.SIDE)
        vm.setCompareLayout(ComparisonLayout.SIDE_BY_SIDE)
        vm.toggleBeforeAfter()
        vm.toggleBeforeAfter()

        val state = vm.uiState.value
        assertEquals(PhotoAngle.SIDE, state.compareAngle)
        assertEquals(ComparisonLayout.SIDE_BY_SIDE, state.compareLayout)
    }
}
