package woowacourse.shopping.data.mapper

import woowacourse.shopping.data.local.CartProductEntity
import woowacourse.shopping.model.Product

fun Product.toEntity(): CartProductEntity {
    return CartProductEntity(
        name = name,
        price = price,
        imageUrl = imageUrl,
    )
}

fun CartProductEntity.toDomain(): Product {
    return Product(id, name, price, imageUrl, createdAt)
}
