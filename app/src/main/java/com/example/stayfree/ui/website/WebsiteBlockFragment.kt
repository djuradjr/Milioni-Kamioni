package com.example.stayfree.ui.website

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.stayfree.R
import com.example.stayfree.databinding.DialogAddWebsiteBinding
import com.example.stayfree.databinding.FragmentWebsiteBlockBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class WebsiteBlockFragment : Fragment() {

    private var _binding: FragmentWebsiteBlockBinding? = null
    private val binding get() = _binding!!
    private val viewModel: WebsiteBlockViewModel by viewModels()
    private lateinit var adapter: WebsiteBlockAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentWebsiteBlockBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = WebsiteBlockAdapter(
            onToggle = { entity -> viewModel.toggleWebsite(entity) },
            onDelete = { id -> viewModel.deleteWebsite(id) }
        )
        binding.rvWebsites.apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = this@WebsiteBlockFragment.adapter
        }

        binding.fab.setOnClickListener { showAddDialog() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.websites.collectLatest { list ->
                adapter.submitList(list)
                val empty = list.isEmpty()
                binding.emptyState.visibility = if (empty) View.VISIBLE else View.GONE
                binding.rvWebsites.visibility = if (empty) View.GONE else View.VISIBLE
            }
        }
    }

    private fun showAddDialog() {
        val dialogBinding = DialogAddWebsiteBinding.inflate(layoutInflater)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.website_add_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.btn_add) { _, _ ->
                val domain = dialogBinding.etDomain.text?.toString()?.trim().orEmpty()
                val capMinutes = dialogBinding.etCap.text?.toString()?.toLongOrNull() ?: 0L
                if (domain.isNotEmpty()) {
                    val capMs = if (capMinutes <= 0) null else capMinutes * 60_000L
                    viewModel.addWebsite(domain, capMs)
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
