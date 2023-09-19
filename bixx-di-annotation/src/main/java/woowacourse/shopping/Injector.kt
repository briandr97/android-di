package woowacourse.shopping

import kotlin.reflect.KClass

@Suppress("UNCHECKED_CAST")
open class Injector(val dependencyContainer: DependencyContainer = DependencyContainer.getSingletonInstance()) {
    open val providerContainer = ProviderContainer.getSingletonInstance(this, dependencyContainer)
    fun <T : Any> inject(target: KClass<T>, qualifierTag: String? = null): T {
        return getFromContainer(target, qualifierTag) ?: createByProvider(target, qualifierTag)
    }

    private fun <T : Any> createByProvider(target: KClass<T>, qualifierTag: String?): T {
        return providerContainer.getInstance(target, qualifierTag) as T
    }

    private fun <T : Any> getFromContainer(target: KClass<T>, qualifierTag: String?): T? {
        return dependencyContainer.getInstance(target, qualifierTag) as T?
    }

    companion object {
        private var Instance: Injector? = null
        fun getSingletonInstance(): Injector {
            return Instance ?: synchronized(this) {
                return Instance ?: Injector().also { Instance = it }
            }
        }
    }
}
