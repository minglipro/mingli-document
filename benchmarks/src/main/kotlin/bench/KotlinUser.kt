package bench

data class KotlinUser(
    val id: Int,
    val name: String,
    val active: Boolean,
    val score: Double,
    val tags: List<String>,
    val address: KotlinAddress,
)