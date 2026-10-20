# Utility distribution and house service (3.0.25)

All existing poles and transmission towers are three blocks taller. Existing block/item IDs, overhead spans, transformer ratios and streetlight settings remain compatible. Saved poles reserve extra clearance when the surrounding chunks load; the upgrade never replaces player-built blocks.

## Overhead circuits and service drops

The new **Single Phase Utility Pole** has one overhead conductor. **Three Phase Utility Pole** has three independent conductors, A, B and C. In a pole's menu, select **Cable** before clicking two poles with an intact power cable. Select the same phase at both ends. A connection between mismatched new phase or voltage terminals is rejected.

Every pole, including the existing variants and towers, has an independent secondary rack. Select **120/240 V service drop** to link all three service channels at once. Use a cable spool for long bundles; ordinary stackable cable items are limited to one stack of paid conductor length. The bundle uses three metres of cable per block of span and renders three sagging conductors. Each has its own resistance and thermal state. Individual L1 120 V, L2 120 V and 240 V terminals are also available. Unpowered racks pass the secondary through; they do not generate voltage.

The simulator uses DC-equivalent circuits. A/B/C are independent power networks, not AC phase angles. The service bundle represents two independent 120 V load circuits and one 240 V load circuit, with the return reference implicit. It does not simulate a physical neutral conductor, split-phase waveforms or neutral imbalance. All three outputs draw from the same real transformer, so their combined power cannot exceed its rating.

## A working pole-to-house connection

1. Feed a **Three Phase Underground Feeder Pole** with three separate floor cables: left A, right B, rear C. The front follows placement direction.
2. Link its A/B/C overhead terminals to the matching terminals on other three-phase poles. Protection poles have separate **Line** and **Load** terminals; the outgoing circuit must connect to Load.
3. Install a **Mains Step-down Unit** in a transformer pole by right-clicking with it, or using its inventory slot. Choose A, B or C on a three-phase transformer pole. The transformer uses only that phase.
4. Choose **10, 25, 50, 75 or 100 kW**. This is the total transformer output budget, shared by the lamp, normal base output and all service channels. Choose 120 or 240 V for the normal base output; the service rack always provides both 120 V circuits and the 240 V circuit. The standalone Mains Step-down Unit has the same selectable ratings. Input current, available source power, feeder resistance and thermal limits still apply.
5. Connect the secondary service bundle along secondary racks or **120/240V Secondary Utility Poles**, then to a wall-mounted **120/240V Service Weatherhead** on the house. The **Secondary Street Light Pole** runs its lamp directly from L1; it needs no transformer or primary fuse.
6. Connect the weatherhead to the **incoming service bundle** on a **North American Meter Base**. Insert an **Analog Glass Service Meter** or **Smart Digital Service Meter**.
7. Connect the meter's **outgoing service bundle** to a **Twelve Circuit American Breaker Panel**.
8. Insert existing Electrical Breaker items into the panel. Give each desired branch a unique wireless power channel, then enter that name on lamps or outlets. Devices still need their normal bulbs and cable inserts. Outlets can supply the existing flexible cords.

The weatherhead also terminates individual circuits into ordinary wall cables: left L1, right L2, and the top/bottom circuit selected in its menu. On a meter, top is the selected incoming circuit and bottom is the selected outgoing circuit; side terminals expose the outgoing 120 V branches. Use a service bundle between cabinets to carry all three circuits together. For a single ordinary 240 V feeder, select 240 V at the weatherhead/meter and feed the panel's top 240 V terminal; only branches set to that powered bus will receive power. A single 240 V cable does not supply the separate L1/L2 buses.

## Protection and switching

- **Fused Cutout Poles**, single- and three-phase, contain cutouts without a transformer. Select the trip current in amperes. Each phase's fuse protects its own load terminal.
- Transformer poles, including the existing converter and streetlight poles, have a primary fuse. A sustained output overload also operates the fuse when the converter is already limiting current. The old 4:1 converter pole keeps its original voltage ratio; its selectable kW value sets its fuse protection.
- A blown fuse drops its door open and plays a pop. Inspect/remove the overload, then right-click holding a **Pole Fuse Link**. A blown link cannot be reset by toggling the GUI or picking up/replacing its pole. Manual operation under load makes an arc; closing or re-fusing plays a quieter arc only if current actually starts flowing.
- **Three Phase Recloser Pole** opens every phase when any phase exceeds pickup current. Configure 1–2000 A pickup, 1–60 seconds retry delay and 1–5 failed reclosures. The first trip starts the retry sequence; each failed automatic reclosure counts toward lockout. Ten seconds of stable operation clears the failed-retry counter. Manual Open prevents automatic closing. Locked-out and manually open states survive world restart. Inspect the fault and use **Reset lockout** to close again.
- **Three Phase Gang Operated Air Break Switch Pole** opens/closes all three load terminals together. It does not automatically detect overloads.
- **Automatic Voltage Regulator Poles**, single- and three-phase, use passive, power-conserving taps. Their input cutouts use the same replaceable Pole Fuse Links. Each phase adjusts independently toward the selected target, within ±10% of its input voltage. Tap movement is at most one percentage point per second. Hold Tap freezes the current ratios. A regulator cannot produce power from an unpowered line or replace a large-ratio transformer.
- **Three Phase Pole Mounted Capacitor Bank** has three separate capacitor branches with inrush resistance and a high-resistance discharge path. It stores charge and smooths DC-equivalent voltage changes. AC reactive power and power-factor correction are not simulated.

Protection/retry clocks advance with physical simulation time, not solver convergence passes. The recloser bumps audibly on each operation. Opening a fuse or switch is not a replacement for clearing the original fault.

## Meters and panel

Both meter inserts complete the real service circuit. Removing either meter opens all three channels. The glass analog meter has five rotating register needles and a marked spinning disk. The smart meter displays imported kWh and has a manual service disconnect. Both menus show independent import/export registers; exporting never subtracts from the import register. Registers belong to the installed meter base and survive swapping inserts, chunk unloading and restart. Unloaded time is not billed. A server-side `setRemoteDisconnect` hook is reserved for a future remote integration; no remote communications system is advertised in this release.

The panel has twelve single-item breaker slots in two banks of six. Left slots default to L1 120 V and right slots to L2 120 V. Each branch may instead select the 240 V bus. Its breaker item supplies its ampere rating; an empty slot is open. The old unrated breaker is treated as a 400 A breaker. Every branch has a manual switch, persistent trip latch, wireless power channel and optional wireless control channel. Control choices match Lamp Supplies: Any On, All On or Toggle. Blank control means enabled; `true` and `false` are also supported. Wireless range is selectable from 32 to 512 blocks, within the same dimension.

Ordinary panel wall cables connect left L1, right L2, top 240 V, and bottom the selected circuit. Overhead link selection also exposes the selected branch separately. Configure voltage before attaching loads. There is no additional whole-panel main breaker: use upstream fused/recloser protection and appropriately sized cables for the service rating. Individual 240 V branches use one ELN breaker circuit, rather than modeling a physical paired AC breaker.

Wireless lamp demand is charged to its own real branch. Wireless cord outlets connect passively to that same protected branch. Neither mode creates a second source. Tripping one branch leaves the others available. On unloading or removal, wireless bridges are removed before the panel's loads leave the simulator. Saved overhead spans are deduplicated between their two endpoint records after loading.

## Aura-style ceiling fan

The **Aura Five Blade Ceiling Fan** follows the supplied fan photos: satin gray finish, five swept blades with narrow roots and broad rounded ends, a roughly 36-inch span and seven recessed downward lights. Install up to seven bulbs of any supported lamp technology, using a matching voltage. Its fan and lights have independent controls, gradual startup/coast-down and the existing fan sound. It supports wireless lamp/panel channels, direct cables and the flexible cord system. It can mount on the same ceiling/slab surfaces as the other ceiling fixtures.

![Aura-style five-blade fan with seven recessed lights in Minecraft](assets/aura-five-blade-fan.png)

## Building and validation

Build the matching source tag using the [Ubuntu 24.04 instructions](building-on-ubuntu-24.04.md). The release page intentionally has no compiled jar; attach the locally compiled `ElectricalAge-3.0.25.jar`.

Release validation: **725 unit tests passed with zero failures or skips**, and **14 native Minecraft checks passed**, including a cold-world restart and client receipt of all four new operating sounds. The native report for this validation used world `utility-distribution-1792482685255`.

The opt-in native campaign is `-Deln.utilityDistributionChecks=true` with `runClient`. It places the registered production equipment, feeds three independent voltage legs through actual cables and overhead links, exercises panel and meter GUI inventory/buttons, observes protection sounds and saves/reopens its world. Its JSON report is `run/client/utility-distribution-checks.json`; a successful report must have no failure and the expected current world name/checks. This campaign is disabled in normal play.
