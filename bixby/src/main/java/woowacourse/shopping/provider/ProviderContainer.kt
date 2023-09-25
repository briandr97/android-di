package woowacourse.shopping.provider

import woowacourse.shopping.Inject
import woowacourse.shopping.Injector
import woowacourse.shopping.Qualifier
import woowacourse.shopping.Singleton
import woowacourse.shopping.dependency.DependencyContainer
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.valueParameters
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.jvmErasure

class ProviderContainer(
    private val injector: Injector,
    private val dependencyContainer: DependencyContainer,
) {
    private val providerContainer = mutableMapOf<KClass<*>, MutableList<ProvideSupply>>()

    fun addProvider(provider: Any) {
        provider::class.declaredMemberFunctions.forEach {
            val returnClassType = it.returnType.jvmErasure
            if (providerContainer[returnClassType] == null) {
                providerContainer[returnClassType] = mutableListOf(ProvideSupply(it, provider))
                return@forEach
            }
            providerContainer[returnClassType]!!.add(ProvideSupply(it, provider))
        }
    }

    // 1. providerContainer에서 반환값이 동일한 ProvideSupply 리스트를 찾는다.
    // 2. ProvideSupply 리스트에서 qualifierTag를 통해 ProvideSupply 를 찾는다.
    // 3. ProvideSupply를 찾으면 함수 파라미터의 인스턴스들을 생성한다.
    // 4. 파라미터 인스턴스들을 넣어 함수를 실행시킨다.
    // 5. Singleton 어노테이션이 provide 함수에 붙어있다면 DependencyContainer에 저장한다.
    // 6. 인스턴스를 반환한다.
    fun getInstance(target: KClass<*>, qualifierTag: String? = null): Any? {
        val provideSupply = getProvideSupply(target, qualifierTag).getOrElse { return null }
        val params = provideSupply.function.valueParameters.map { it.getInstance() }
        val instance = requireNotNull(
            provideSupply.function.call(provideSupply.factory, *params.toTypedArray()),
        ) { "$ERROR_PREFIX $PROVIDE_CALL_FAILED : ${provideSupply.function}" }.apply { injectProperties() }

        storeIfSingleton(provideSupply, instance)
        return instance
    }

    private fun getProvideSupply(target: KClass<*>, qualifierTag: String?): Result<ProvideSupply> {
        val provideSupplies = providerContainer[target]
            ?: return Result.failure(NullPointerException())
        provideSupplies.forEach { provideSupply ->
            if (provideSupply.function.isSameType(target, qualifierTag)) {
                return Result.success(provideSupply)
            }
        }
        throw NoSuchElementException("$ERROR_PREFIX $NO_FUNCTION : $target, $qualifierTag")
    }

    private fun KParameter.getInstance(): Any {
        val qualifierTag = this.findAnnotation<Qualifier>()?.className
        return injector.inject(this.type.jvmErasure, qualifierTag)
    }

    private fun storeIfSingleton(provideSupply: ProvideSupply, instance: Any) {
        if (provideSupply.function.hasAnnotation<Singleton>()) {
            dependencyContainer.addInstance(
                provideSupply.function.returnType.jvmErasure,
                provideSupply.function.annotations,
                instance,
            )
        }
    }

    // 식별자와 반환타입을 비교하여 원하는 provide 함수인지 확인한다.
    private fun KFunction<*>.isSameType(
        target: KClass<*>,
        qualifierTag: String?,
    ): Boolean {
        if (qualifierTag == null) return checkReturnType(target)
        return (checkReturnType(target)) && (checkQualifier(qualifierTag))
    }

    // target에 식별자가 존재하는지, 존재한다면 provide 함수의 식별자와 일치하는지 확인한다.
    private fun KFunction<*>.checkQualifier(qualifierTag: String): Boolean {
        val qualifier = annotations.filterIsInstance<Qualifier>().firstOrNull() ?: return false
        return qualifier.className == qualifierTag
    }

    // 함수의 반환 타입과 타겟의 타입이 일치하는지 확인한다.
    private fun KFunction<*>.checkReturnType(target: KClass<*>): Boolean {
        return returnType.jvmErasure == target
    }

    private fun Any.injectProperties() {
        val properties = this@injectProperties::class.declaredMemberProperties
        properties.forEach {
            it.findAnnotation<Inject>() ?: return@forEach
            val qualifier = it.findAnnotation<Qualifier>()
            it.isAccessible = true
            this@injectProperties::class.java.getDeclaredField(it.name).apply {
                isAccessible = true
                set(
                    this@injectProperties,
                    injector.inject(it.returnType.jvmErasure, qualifier?.className),
                )
            }
        }
    }

    fun clear() {
        providerContainer.clear()
    }

    companion object {
        private const val ERROR_PREFIX = "[ERROR]"
        private const val NO_FUNCTION = "찾으려는 생성 함수가 없습니다."
        private const val PROVIDE_CALL_FAILED = "생성 함수 호출에 실패했습니다."

        private var Instance: ProviderContainer? = null
        fun getSingletonInstance(
            injector: Injector,
            dependencyContainer: DependencyContainer,
        ): ProviderContainer {
            return Instance ?: synchronized(this) {
                return Instance ?: ProviderContainer(injector, dependencyContainer)
                    .also { Instance = it }
            }
        }
    }
}
