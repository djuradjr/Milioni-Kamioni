package com.example.stayfree.ui.common

import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.stayfree.databinding.ViewBackHeaderBinding

fun Fragment.bindBackHeader(header: ViewBackHeaderBinding) {
    header.btnBack.setOnClickListener { findNavController().navigateUp() }
}
