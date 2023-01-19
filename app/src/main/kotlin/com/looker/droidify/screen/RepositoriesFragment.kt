package com.looker.droidify.screen

import android.database.Cursor
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.looker.core.common.extension.setCollapsable
import com.looker.core.common.extension.systemBarsPadding
import com.looker.core.datastore.UserPreferencesRepository
import com.looker.core.datastore.distinctMap
import com.looker.droidify.database.CursorOwner
import com.looker.droidify.service.Connection
import com.looker.droidify.service.SyncService
import com.looker.droidify.utility.Utils
import com.looker.droidify.utility.extension.screenActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.looker.droidify.R.drawable as drawableRes
import com.looker.droidify.R.string as stringRes

@AndroidEntryPoint
class RepositoriesFragment : ScreenFragment(), CursorOwner.Callback {
	private var recyclerView: RecyclerView? = null

	@Inject
	lateinit var userPreferencesRepository: UserPreferencesRepository

	private val toolbarPreferenceFlow
		get() = userPreferencesRepository.userPreferencesFlow.distinctMap { it.allowCollapsingToolbar }

	private val syncConnection = Connection(SyncService::class.java)

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	): View {
		val view = fragmentBinding.root.apply {
			val content = fragmentBinding.fragmentContent
			content.addView(
				RecyclerView(content.context).apply {
					id = android.R.id.list
					layoutManager = LinearLayoutManager(context)
					isMotionEventSplittingEnabled = false
					setHasFixedSize(true)
					adapter = RepositoriesAdapter({ screenActivity.navigateRepository(it.id) },
						{ repository, isEnabled ->
							repository.enabled != isEnabled &&
									syncConnection.binder?.setEnabled(repository, isEnabled) == true
						})
					recyclerView = this
				}
			)
		}
		recyclerView?.systemBarsPadding()
		this.toolbar = fragmentBinding.toolbar
		this.collapsingToolbar = fragmentBinding.collapsingToolbar
		this.appBarLayout = fragmentBinding.appbarLayout
		return view
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		syncConnection.bind(requireContext())
		screenActivity.cursorOwner.attach(this, CursorOwner.Request.Repositories)
		screenActivity.onToolbarCreated(toolbar)
		toolbar.menu.add(stringRes.add_repository)
			.setIcon(Utils.getToolbarIcon(toolbar.context, drawableRes.ic_add))
			.setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS)
			.setOnMenuItemClickListener {
				view.post { screenActivity.navigateAddRepository() }
				true
			}
		collapsingToolbar.title = getString(stringRes.repositories)
		viewLifecycleOwner.lifecycleScope.launch {
			repeatOnLifecycle(Lifecycle.State.RESUMED) {
				toolbarPreferenceFlow.collect {
					appBarLayout.setCollapsable(it)
				}
			}
		}
	}

	override fun onDestroyView() {
		super.onDestroyView()

		recyclerView = null

		syncConnection.unbind(requireContext())
		screenActivity.cursorOwner.detach(this)
	}

	override fun onCursorData(request: CursorOwner.Request, cursor: Cursor?) {
		(recyclerView?.adapter as? RepositoriesAdapter)?.cursor = cursor
	}
}
