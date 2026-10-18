package com.spendwise.backend

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class SpendWiseApplication

fun main(args: Array<String>) {
    runApplication<SpendWiseApplication>(*args)
}
