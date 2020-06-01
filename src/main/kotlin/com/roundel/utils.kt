package com.roundel

data class OpenFloatRange(val from: Float, val to: Float)
infix fun Float.open(to: Float) = OpenFloatRange(this, to)
operator fun OpenFloatRange.contains(f: Float) = from < f && f < to