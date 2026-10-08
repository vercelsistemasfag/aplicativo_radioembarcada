package br.com.radioembarcada.model

data class Tenant(
    val clientId: String,
    val activationCode: String,
    val companyName: String,
    val radioName: String,
    val primaryColor: String,
)
