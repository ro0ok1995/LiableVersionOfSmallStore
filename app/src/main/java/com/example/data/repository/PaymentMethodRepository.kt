package com.example.data.repository

import com.example.data.db.FinancialAccount
import com.example.data.db.PaymentMethod
import com.example.data.db.PaymentMethodDao
import com.example.data.db.SmallStoreDatabase
import kotlinx.coroutines.flow.Flow

class PaymentMethodRepository(
    private val paymentMethodDao: PaymentMethodDao
) {
    constructor(database: SmallStoreDatabase) : this(database.paymentMethodDao())

    val allPaymentMethods: Flow<List<PaymentMethod>> = paymentMethodDao.getAllPaymentMethods()

    suspend fun createPaymentMethod(paymentMethod: PaymentMethod) {
        paymentMethodDao.insertPaymentMethod(paymentMethod)
    }

    suspend fun getPaymentMethodById(id: String): PaymentMethod? {
        return paymentMethodDao.getPaymentMethodById(id)
    }

    fun getActivePaymentMethods(): Flow<List<PaymentMethod>> {
        return paymentMethodDao.getActivePaymentMethods()
    }

    suspend fun getAllPaymentMethodsSync(): List<PaymentMethod> {
        return paymentMethodDao.getAllPaymentMethodsSync()
    }

    suspend fun updatePaymentMethod(paymentMethod: PaymentMethod) {
        paymentMethodDao.updatePaymentMethod(paymentMethod)
    }

    companion object {
        val CANONICAL_ACCOUNTS = listOf(
            FinancialAccount("acc_cash", "نقدي كاش", "CASH"),
            FinancialAccount("acc_bank_palestine", "بنك فلسطين", "BANK"),
            FinancialAccount("acc_palpay", "محفظة بال باي", "E_WALLET"),
            FinancialAccount("acc_jawwalpay", "محفظة جوال باي", "E_WALLET"),
            FinancialAccount("acc_maltachat", "حساب مالتشات", "E_WALLET")
        )

        val CANONICAL_PAYMENT_METHODS = listOf(
            PaymentMethod("pm_cash", "نقدي كاش", "CASH"),
            PaymentMethod("pm_bank_palestine", "بنك فلسطين", "BANK"),
            PaymentMethod("pm_palpay", "محفظة بال باي", "E_WALLET"),
            PaymentMethod("pm_jawwalpay", "محفظة جوال باي", "E_WALLET"),
            PaymentMethod("pm_maltachat", "حساب مالتشات", "E_WALLET")
        )

        /**
         * Ensures that all canonical payment methods and payment methods corresponding
         * to any financial accounts exist in the database, without duplicating or overwriting existing rows.
         */
        suspend fun ensurePaymentMethods(database: SmallStoreDatabase) {
            val paymentMethodDao = database.paymentMethodDao()
            val accountDao = database.financialAccountDao()
            val existingMethods = paymentMethodDao.getAllPaymentMethodsSync().associateBy { it.id }
            val now = System.currentTimeMillis()

            val methodsToInsert = mutableListOf<PaymentMethod>()

            for (canonical in CANONICAL_PAYMENT_METHODS) {
                if (!existingMethods.containsKey(canonical.id)) {
                    methodsToInsert.add(canonical.copy(createdAt = now))
                }
            }

            val existingAccounts = accountDao.getAllAccountsSync()
            for (account in existingAccounts) {
                val correspondingPmId = if (account.id.startsWith("acc_")) {
                    "pm_" + account.id.removePrefix("acc_")
                } else {
                    "pm_${account.id}"
                }
                if (!existingMethods.containsKey(correspondingPmId) && methodsToInsert.none { it.id == correspondingPmId }) {
                    methodsToInsert.add(
                        PaymentMethod(
                            id = correspondingPmId,
                            name = account.name,
                            type = account.type,
                            isActive = account.isActive,
                            createdAt = now
                        )
                    )
                }
            }

            if (methodsToInsert.isNotEmpty()) {
                paymentMethodDao.insertPaymentMethods(methodsToInsert)
            }
        }

        /**
         * Ensures default financial accounts and their associated payment methods exist.
         */
        suspend fun ensureDefaultConfiguration(database: SmallStoreDatabase) {
            val accountDao = database.financialAccountDao()
            val existingAccounts = accountDao.getAllAccountsSync().associateBy { it.id }
            val now = System.currentTimeMillis()

            val accountsToInsert = CANONICAL_ACCOUNTS.filterNot { existingAccounts.containsKey(it.id) }
                .map { it.copy(createdAt = now, updatedAt = now) }
            if (accountsToInsert.isNotEmpty()) {
                accountDao.insertAccounts(accountsToInsert)
            }
            ensurePaymentMethods(database)
        }

        /**
         * Resolves the authoritative active PaymentMethod for a financial operation.
         * Preserves the association between the selected financial account and its corresponding payment method.
         */
        suspend fun resolvePaymentMethodForOperation(
            database: SmallStoreDatabase,
            explicitPaymentMethodId: String?,
            financialAccount: FinancialAccount
        ): PaymentMethod {
            val paymentMethodDao = database.paymentMethodDao()

            require(financialAccount.isActive) {
                "Financial account is inactive: ${financialAccount.id}"
            }

            // Always heal missing rows before resolving. Never select an unrelated method
            // merely because it shares a type (e.g. a generic BANK method for Bank of Palestine).
            ensurePaymentMethods(database)

            val expectedId = if (financialAccount.id.startsWith("acc_")) {
                "pm_" + financialAccount.id.removePrefix("acc_")
            } else {
                "pm_${financialAccount.id}"
            }

            if (!explicitPaymentMethodId.isNullOrBlank() && explicitPaymentMethodId != expectedId) {
                throw IllegalArgumentException(
                    "Payment method $explicitPaymentMethodId does not match financial account ${financialAccount.id}; expected $expectedId"
                )
            }

            val methodId = explicitPaymentMethodId ?: expectedId
            val method = paymentMethodDao.getPaymentMethodById(methodId)
                ?: throw IllegalArgumentException(
                    "Payment method not found: $methodId for financial account ${financialAccount.id}"
                )

            require(method.isActive) { "Payment method is inactive: ${method.id}" }
            require(method.type == financialAccount.type) {
                "Payment method ${method.id} type ${method.type} does not match financial account ${financialAccount.id} type ${financialAccount.type}"
            }
            return method
        }
    }
}
