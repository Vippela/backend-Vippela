package com.vippela.backend.linking

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "device_links")
class DeviceLink(
    @Id @GeneratedValue(strategy = GenerationType.UUID) val id: UUID? = null,
    val ownerUserId: UUID,
    val token: String,
    var linkedDeviceId: String? = null,
    var status: String = "pending",
    val expiresAt: Instant,
    val createdAt: Instant = Instant.now(),
    var ownerKeyHash: String? = null,
    var deviceKeyHash: String? = null,
    @Column(unique = true) var deviceBinding: String? = null,
    @Column(unique = true) var memberBinding: String? = null,
    var memberKey: String? = null,
    var memberName: String? = null,
    var ownerName: String? = null,
    var revision: Long = 0,
    var appliedRevision: Long = -1,
    var protectionEnabled: Boolean = false,
    var lastSeenAt: Instant? = null,
    @Column(nullable = false, columnDefinition = "boolean default false")
    var usagePermission: Boolean = false,
    var usageUpdatedAt: Instant? = null,
    var usageSince: Instant? = null,
    @Column(nullable = false, columnDefinition = "varchar(255) default 'UTC'")
    var usageZone: String = "UTC",
    @ElementCollection
    @CollectionTable(name = "link_usage", joinColumns = [JoinColumn(name = "link_id")])
    @MapKeyColumn(name = "bucket", length = 300) @Column(name = "milliseconds")
    var usage: MutableMap<String, Long> = mutableMapOf(),
    @ElementCollection
    @CollectionTable(name = "link_icons", joinColumns = [JoinColumn(name = "link_id")])
    @MapKeyColumn(name = "package_name") @Column(name = "png_base64", length = 12000)
    var icons: MutableMap<String, String> = mutableMapOf(),
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "link_apps", joinColumns = [JoinColumn(name = "link_id")])
    @MapKeyColumn(name = "package_name") @Column(name = "app_label")
    var apps: MutableMap<String, String> = mutableMapOf(),
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "link_blocked_apps", joinColumns = [JoinColumn(name = "link_id")])
    @Column(name = "package_name")
    var blockedPackages: MutableSet<String> = mutableSetOf(),
)
