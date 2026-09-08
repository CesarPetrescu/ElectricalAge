"""Named multiplayer contracts shared by the driver and its fail-closed report gate."""


def plan(profile):
    if profile not in ("standalone", "create"):
        raise ValueError(profile)
    groups = []

    def command(role, name, action, **args):
        return {"role": role, "id": name, "action": action, **args}

    def one(role, name, action, **args):
        groups.append([command(role, name, action, **args)])

    def both(name, action, **args):
        groups.append([command(role, name, action, **args) for role in ("alpha", "beta")])

    one("server", "packaged-server", "boot")
    both("packaged-client", "boot")
    one("server", "place-fixtures", "fixture")
    one("alpha", "first-join", "join")
    one("server", "first-player", "players", names=["ElnAlpha"])
    one("server", "first-home", "home")
    one("alpha", "initial-source", "source", voltage=12)
    one("alpha", "initial-monitor", "monitor")
    one("alpha", "change-source", "source-set", voltage=24)
    one("server", "live-circuit", "circuit", voltage=24)
    one("alpha", "source-ack", "source", voltage=24)
    one("beta", "late-join", "join")
    one("server", "two-identities", "players", names=["ElnAlpha", "ElnBeta"])
    one("server", "both-home", "home")
    both("late-source", "source", voltage=24)
    both("late-monitor", "monitor")
    one("alpha", "alpha-holds", "key", pressed=True)
    one("server", "key-isolation", "keys", keys={"ElnAlpha": True, "ElnBeta": False})
    one("beta", "beta-holds", "key", pressed=True)
    one("server", "both-hold", "keys", keys={"ElnAlpha": True, "ElnBeta": True})
    one("alpha", "alpha-releases", "key", pressed=False)
    one("server", "release-isolation", "keys", keys={"ElnAlpha": False, "ElnBeta": True})
    one("beta", "disconnect-held-key", "disconnect")
    one("server", "beta-gone", "players", names=["ElnAlpha"])
    one("server", "logout-clears-key", "keys", keys={"ElnAlpha": False, "ElnBeta": False})
    one("beta", "reconnect", "join")
    one("server", "reconnect-home", "home")
    one("server", "reconnect-clears-key", "keys", keys={"ElnAlpha": False, "ElnBeta": False})
    both("reconnect-source", "source", voltage=24)
    both("reconnect-monitor", "monitor")
    if profile == "create":
        both("open-create-menu", "open-adapter")
        both("create-initial", "adapter", ratio=8, engaged=True)
        one("alpha", "create-disengage", "adapter-command", button=0)
        both("create-disengaged", "adapter", ratio=8, engaged=False)
        one("beta", "create-select-gear", "adapter-command", button=5)
        both("create-gear-sync", "adapter", ratio=2, engaged=False)
        one("alpha", "create-engage", "adapter-command", button=0)
        both("create-live-speed", "adapter", ratio=2, engaged=True)
        one("server", "create-server-speed", "adapter", ratio=2, engaged=True)
        both("close-create-menu", "close")
    both("open-shared-monitor", "open-monitor")
    one("alpha", "real-print-button", "print")
    one("server", "print-transaction", "printed")
    both("printed-output-sync", "printed")
    both("duplicate-print-requests", "print-request")
    both("concurrent-output-transfer", "take-print")
    one("server", "no-duplicated-print", "one-print-total")
    both("inventory-after-transfer", "inventory")
    both("close-monitor", "close")
    one("server", "travel-nether", "nether")
    both("nether-arrival", "dimension", dimension="minecraft:the_nether")
    one("server", "return-overworld", "home")
    both("overworld-arrival", "dimension", dimension="minecraft:overworld")
    both("dimension-source-sync", "source", voltage=24)
    both("dimension-monitor-sync", "monitor")
    one("server", "leave-fixture-chunk", "away")
    both("client-chunk-unloaded", "unloaded")
    one("server", "server-chunk-unloaded", "unloaded")
    one("server", "reload-fixture-chunk", "home")
    both("reload-source-sync", "source", voltage=24)
    both("reload-monitor-sync", "monitor")
    one("server", "reload-circuit", "circuit", voltage=24)
    if profile == "create":
        both("reload-create-sync", "adapter", ratio=2, engaged=True)
    both("disconnect-for-restart", "disconnect")
    one("server", "save-and-stop", "stop")
    # Driver starts a fresh dedicated-server JVM before issuing this command.
    one("server", "restarted-packaged-server", "boot")
    both("join-after-restart", "join")
    one("server", "restart-home", "home")
    both("restart-source-sync", "source", voltage=24)
    both("restart-monitor-sync", "monitor")
    one("server", "restart-circuit", "circuit", voltage=24)
    one("server", "restart-monitor", "monitor")
    one("server", "restart-print-conservation", "one-print-total")
    both("inventory-after-restart", "inventory")
    one("server", "restart-key-reset", "keys", keys={"ElnAlpha": False, "ElnBeta": False})
    if profile == "create":
        both("restart-create-sync", "adapter", ratio=2, engaged=True)
        one("server", "restart-create-speed", "adapter", ratio=2, engaged=True)
    both("close-clients", "stop")
    one("server", "final-save", "stop")
    return groups


def validate(results, profile, jar_sha256):
    expected = {(c["role"], c["id"]): c for group in plan(profile) for c in group}
    actual = {(r["role"], r["id"]): r for r in results}
    if len(actual) != len(results) or actual.keys() != expected.keys():
        raise ValueError(f"Missing, unexpected or duplicate contracts: {expected.keys() - actual.keys()}")
    for key, result in actual.items():
        if result.get("status") != "passed" or result.get("action") != expected[key]["action"]:
            raise ValueError(f"Failed/invalid contract {key}: {result.get('detail')}")
    runtime = [actual[("server", "packaged-server")], *[actual[(r, "packaged-client")] for r in ("alpha", "beta")]]
    if len({r["pid"] for r in runtime}) != 3:
        raise ValueError("Expected three independent JVM processes")
    for r in runtime + [actual[("server", "restarted-packaged-server")]]:
        obs = r["observation"]
        if obs.get("jarSha256") != jar_sha256 or obs.get("create") != (profile == "create"):
            raise ValueError("Wrong packaged JAR or mod profile")
        if r["role"] == "server" and obs.get("dedicated") is not True:
            raise ValueError("Not a dedicated server")
        if r["role"] != "server" and obs.get("integratedServer") is not False:
            raise ValueError("Integrated-server client cannot prove multiplayer")
    if actual[("server", "restarted-packaged-server")]["pid"] == runtime[0]["pid"]:
        raise ValueError("Server did not restart in a new JVM")
    joins = [actual[("alpha", "first-join")], actual[("beta", "late-join")]]
    if len({r["observation"].get("uuid") for r in joins}) != 2:
        raise ValueError("Clients must have distinct player identities")
    for r, name in zip(joins, ("ElnAlpha", "ElnBeta")):
        if r["observation"].get("name") != name or r["observation"].get("integratedServer") is not False:
            raise ValueError("Wrong client identity or connection type")
    for phase in ("inventory-after-transfer", "inventory-after-restart"):
        counts = [actual[(role, phase)]["observation"].get("prints") for role in ("alpha", "beta")]
        if any(type(n) is not int or n < 0 for n in counts) or sum(counts) != 1:
            raise ValueError(f"Client inventories disagree / duplicate output: {counts}")
    return len(results)
