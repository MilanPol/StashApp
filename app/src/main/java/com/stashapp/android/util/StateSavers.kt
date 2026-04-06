package com.stashapp.android.util

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.mapSaver
import com.stashapp.shared.domain.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * Custom savers for domain objects that are not naturally Parcelable or saveable by Android.
 * These allow rememberSaveable to persist complex app state across configuration changes (like rotation).
 */
object StateSavers {

    val ExpirationDateSaver = Saver<ExpirationDate?, String>(
        save = { it?.date?.toString() ?: "" },
        restore = { if (it.isEmpty()) null else ExpirationDate(LocalDate.parse(it)) }
    )

    val QuantitySaver = mapSaver(
        save = { mapOf("amount" to it.amount.toString(), "unit" to it.unit.name) },
        restore = { Quantity(BigDecimal(it["amount"] as String), MeasurementUnit.valueOf(it["unit"] as String)) }
    )

    val StorageLocationSaver = mapSaver(
        save = { 
            mapOf(
                "id" to it.id, 
                "name" to it.name, 
                "icon" to it.icon, 
                "parentId" to it.parentId
            ) 
        },
        restore = { 
            StorageLocation(
                id = it["id"] as String, 
                name = it["name"] as String, 
                icon = it["icon"] as String, 
                parentId = it["parentId"] as? String
            ) 
        }
    )

    val CategorySaver = mapSaver(
        save = { 
            mapOf(
                "id" to it.id, 
                "name" to it.name, 
                "icon" to it.icon, 
                "leadDays" to it.defaultLeadDays
            ) 
        },
        restore = { 
            Category(
                id = it["id"] as String, 
                name = it["name"] as String, 
                icon = it["icon"] as String, 
                defaultLeadDays = it["leadDays"] as? Int
            ) 
        }
    )

    val InventoryEntrySaver = mapSaver(
        save = { 
            mapOf(
                "id" to it.id,
                "name" to it.name,
                "amount" to it.quantity.amount.toString(),
                "unit" to it.quantity.unit.name,
                "expiration" to it.expirationDate?.date?.toString(),
                "locationId" to it.storageLocationId,
                "categoryId" to it.categoryId,
                "openedAt" to it.openedAt?.toString()
            )
        },
        restore = {
            InventoryEntry(
                id = it["id"] as String,
                name = it["name"] as String,
                quantity = Quantity(BigDecimal(it["amount"] as String), MeasurementUnit.valueOf(it["unit"] as String)),
                expirationDate = (it["expiration"] as? String)?.let { s -> ExpirationDate(LocalDate.parse(s)) },
                storageLocationId = it["locationId"] as? String,
                categoryId = it["categoryId"] as? String,
                openedAt = (it["openedAt"] as? String)?.let { s -> Instant.parse(s) }
            )
        }
    )

    val CatalogProductSaver = mapSaver(
        save = {
            mapOf(
                "ean" to it.ean,
                "name" to it.name,
                "brand" to it.brand,
                "amount" to it.defaultQuantity?.amount?.toString(),
                "unit" to it.defaultQuantity?.unit?.name
            )
        },
        restore = {
            CatalogProduct(
                ean = it["ean"] as String,
                name = it["name"] as String,
                brand = it["brand"] as? String,
                defaultQuantity = (it["amount"] as? String)?.let { amt ->
                    Quantity(BigDecimal(amt), MeasurementUnit.valueOf(it["unit"] as String))
                }
            )
        }
    )

    val RecipeIngredientSaver = mapSaver(
        save = {
            mapOf(
                "id" to it.id,
                "recipeId" to it.recipeId,
                "name" to it.name,
                "quantity" to it.quantity,
                "unit" to it.unit.name,
                "notes" to it.notes
            )
        },
        restore = {
            RecipeIngredient(
                id = it["id"] as String,
                recipeId = it["recipeId"] as String,
                name = it["name"] as String,
                quantity = it["quantity"] as? Double,
                unit = MeasurementUnit.valueOf(it["unit"] as String),
                notes = it["notes"] as? String
            )
        }
    )

    fun <T : Any> listSaver(itemSaver: Saver<T, Any>) = listSaver<MutableList<T>, Any>(
        save = { list -> list.map { with(itemSaver) { save(it)!! } } },
        restore = { data -> data.map { with(itemSaver) { restore(it)!! } }.toMutableList() }
    )

    /**
     * Wraps a non-nullable saver to handle nullable types correctly.
     * Uses a List wrapper to satisfy the non-null constraint on the saved state.
     */
    fun <T : Any, S : Any> nullableSaver(itemSaver: Saver<T, S>) = Saver<T?, Any>(
        save = { it?.let { listOf(with(itemSaver) { save(it)!! }) } ?: emptyList<Any>() },
        restore = { (it as? List<*>)?.firstOrNull()?.let { saved -> with(itemSaver) { restore(saved as S) } } }
    )
}
