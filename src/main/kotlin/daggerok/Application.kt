package daggerok

import java.sql.ResultSet
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.update
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.http.HttpStatus.ACCEPTED
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@SpringBootApplication
class Application

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}

data class Customer(val id: Long? = null, val name: String = "")

interface CustomerService {
    fun addOrUpdateCustomer(customer: Customer): Unit
    fun getCustomer(id: Long): Customer?
    fun getAllCustomers(): Iterable<Customer>
}

object ExposedCustomer : Table("customers") {
    val id = long("id").autoIncrement()
    val name = varchar("name", 255)
}

@Service
@Transactional(readOnly = true)
class JdbcTemplateCustomerService(private val jdbcTemplate: NamedParameterJdbcTemplate) : CustomerService {

    @Transactional(readOnly = false)
    override fun addOrUpdateCustomer(customer: Customer) {
        if (customer.id == null) ExposedCustomer.insert { it[name] = customer.name }
        else ExposedCustomer.update(where = { ExposedCustomer.id.eq(customer.id) }) { it[name] = customer.name }
    }

    override fun getCustomer(id: Long): Customer? = run {
        val execution = kotlin
            .runCatching {
                val customerRowMapper: (rs: ResultSet, rowNum: Int) -> Customer? = { rs, _ ->
                    Customer(rs.getLong("id"), rs.getString("name"))
                }
                jdbcTemplate.queryForObject(
                    "SELECT c.* FROM customers c WHERE c.id = :id",
                    mapOf("id" to id),
                    customerRowMapper
                )
            }
            .onFailure { println("Error: ${it.message}") }
        if (execution.isFailure) null
        else execution.getOrNull()
    }

    // step 4: Rename useless lambda variable name to _ underscore:
    override fun getAllCustomers(): Iterable<Customer> =
        jdbcTemplate.query("SELECT * FROM customers") { rs, _ ->
            Customer(rs.getLong("id"), rs.getString("name"))
        }

    // // step 3: Move lambda put of expression as it last argument:
    // override fun getAllCustomers(): Iterable<Customer> =
    //     jdbcTemplate.query("SELECT * FROM customers") { rs, rowNum ->
    //         Customer(rs.getLong("id"), rs.getString("name"))
    //     }

    // // step 2: Remove redundant RowMapper type definition from lambda:
    // override fun getAllCustomers(): Iterable<Customer> =
    //     jdbcTemplate.query("SELECT * FROM customers", { rs, rowNum ->
    //         Customer(rs.getLong("id"), rs.getString("name"))
    //     })

    // // step 1: Use columnLabel instead of columnIndex
    // override fun getAllCustomers(): Iterable<Customer> =
    //     jdbcTemplate.query("SELECT * FROM customers", org.springframework.jdbc.core.RowMapper { rs, rowNum ->
    //         Customer(rs.getLong("id"), rs.getString("name"))
    //     })

    // // step 0:
    // override fun getAllCustomers(): Iterable<Customer> =
    //     jdbcTemplate.query("SELECT * FROM customers", org.springframework.jdbc.core.RowMapper { rs, rowNum ->
    //         Customer(rs.getLong(1), rs.getString(2))
    //     })
}

@RestController
class CustomersResource(private val customerService: CustomerService) {

    @ResponseStatus(ACCEPTED)
    @PostMapping("/api/v1/customers")
    fun postOrPutCustomer(@RequestBody customer: Customer) =
        customerService.addOrUpdateCustomer(customer)

    @GetMapping("/api/v1/customers/{id}")
    fun getCustomers(@PathVariable id: Long) =
        customerService.getCustomer(id)

    @GetMapping("/api/v1/customers")
    fun getAllCustomers() =
        customerService.getAllCustomers()
}
