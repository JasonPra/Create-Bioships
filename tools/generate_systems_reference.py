"""Generate readable text architecture and an optional paginated PDF (--pdf)."""
from pathlib import Path
import re, json, html, subprocess
ROOT=Path(__file__).resolve().parents[1]; OUT=ROOT/'docs/development'
COMMIT=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()
BASE=ROOT/'src/main/java/org/Azkerash/create_bioships'
# Every source class belongs to one bubble. Edges describe system relationships, not an exhaustive call graph.
GROUPS=[
('boot','Registration and placement','active',['Create_bioships','LivingCoreBlock'],'Registers blocks, items, menus and entities; placing a core creates its Sable body.'),
('core','Creature lifecycle coordinator','active',['LivingCoreBlockEntity'],'Owns subsystem instances and saved state; schedules game ticks and publishes physics intent.'),
('chunks','World loading','active',['BioshipChunks'],'View and simulation leases follow the creature; tickets release on removal or death.'),
('geometry','Body geometry and reach','active',['BioshipStructure','BioshipIntegration','BioshipReach','BodyTopology','BioshipCoreLifecycle'],'Read loaded Sable cells, integrate native machines, distinguish visibility from physical reach.'),
('vision','Perception and memory','active',['BioshipVision','TargetMemory','BioshipPriorities','BioshipLootMemory'],'Directional cones, light, occlusion, observations and three-real-second target memory.'),
('surfaces','Organic surfaces','active',['SurfaceEyeBlock','OneWayWindowBlock','CarapaceBlock','PulseLightBlock'],'Eye mounting, window faces and reusable body block shapes. No automatic staged hull.'),
('needs','Needs and food policy','active',['BioshipNeeds','BioshipNutrition'],'Food, trust, poison and urgent food-task classification; pressing food threshold is 50.'),
('energy','Per-block energy demand','active',['BioshipEnergy','BlockEnergyTable'],'Read body cells; zero-cost producers/connectors; ordinary blocks contribute upkeep.'),
('brain','Behavior selection and goal queue','active',['BioshipBrain','BehaviorPriority','GoalQueue'],'Safety branches, committed work, NEED before GOAL, weighted ranking, one selected Decision per call.'),
('autonomy','Escape and exploration','active',['BioshipAutonomy'],'Threat assessment, retreat and idle wandering. Pilot authority is currently disabled.'),
('route','Ground navigation and gait','active',['BioshipCrawl','GroundRoute','HatchlingFront'],'Full-body routes, stuck handling, paid legs, footing, speed and accessible front-mouth configuration.'),
('support','Balance and self-righting','active',['BioshipSupport','BioshipRighting','BioshipLegAnchors','BioshipLegGrowth','BioshipMountGrowth','AmoebaLegBlock','BioshipAnatomy','BioshipLegEvolution','LegUsageHistory'],'Mass-weighted COM, leg attachments, support polygon, reachable contacts and inverted recovery torque.'),
('water','Swimming and breathing','active',['BioshipAquatic','BioshipRespiration','RespiratoryOrganBlock','ExposureHistory'],'Saved air supply, all-mouth fluid checks, surfacing need and non-flight propulsion intent.'),
('scout','Move the existing core','active',['CoreMotion'],'Reposition through connected anatomy for sight or defense; preserve state and inventory.'),
('collect','Batch assimilation','active',['BioshipAssimilation','BioshipFoodCollection'],'Mouth-bound collection slots, persistent swallowed stock, batch settlement and committed wreck ownership.'),
('transport','Conserved component transport','active',['amoeba.ComponentTransport','AmoebaCoreAdapter','amoeba.AmoebaComponentHost','amoeba.AmoebaComponentProvider','amoeba.ComponentAttachment','BioshipContainerState','integration.sable.BodyCoordinates','integration.biomancy.NativeStorage','amoeba.ComponentMerging'],'Move one actual component/Sable cargo with its complete state; route tube costs before transfer.'),
('salvage','Scavenging and recycling','active',['BioshipScavenging'],'Find eligible wreck sources, keep needed organs, conserve inventories and apply fractional lossy yields.'),
('gut','Mouths and physical gut graph','active',['DigestiveSystem','BioshipDigestiveRepair','FeedingMouthBlockEntity','FeedingMouthBlock','IntestineBlock'],'Assign distinct stomachs, validate tube paths, buffer mouth input and connect food sacs.'),
('stomach','Recipe digestion','active',['StomachBlockEntity','StomachBlock'],'Native Biomancy recipes and output rolls, acid, input queues, local nutrient paste and tube stock.'),
('storage','Shared usable storage','active',['BioshipStorage','BioshipStores'],'Core-first deposits, connected native/private sac stocks, external-first withdrawal and reserve refill.'),
('money','Costs, ledgers and reserve policy','active',['MaterialCost','creature.ResourceAccount','creature.CreatureBodyAccess','MaterialLedger','EmergencyReserve'],'Affordability, payment rollback and refunds; core emergency target 32 flesh / 16 fiber / 8 bone.'),
('plans','Read-only anatomical proposals','active',['BioshipAdaptationPlanner','BioshipDigestiveGrowth','BioshipRecovery'],'Propose funded organ recovery, digestion, storage, reinforcement or safe reclamation; no block edits.'),
('adapt','Execute anatomical adaptation','active',['BioshipAdaptation','BioshipBodyBehavior','BioshipConfiguration'],'Rank body work independently of actions, share layout transaction locks, and execute paid adaptation or native configuration.'),
('metabolism','Metabolism and emergency fuel','active',['BioshipMetabolism'],'One metabolic step at the core cadence; nutrient paste, emergency resources, then starvation damage.'),
('health','Health and intended body','active',['BlockHealthTable','BioshipHealthPool','BioshipHealthChanges','mixin.ComponentHealthChangeMixin','BioshipBody','BioshipDeathCause'],'Health, capacity, intended-cell memory, injury, paid repair and permanent death.'),
('combat','Temporary arms and combat','active',['BioshipSurvivalAdaptations','mixin.SurvivalExplosionMixin','mixin.SurvivalBlastResistanceMixin','BioshipDefense','BioshipCreeperHazard','mixin.CreeperBlastAccess','TentacleStrike','ImpactDamage','BioshipPrey','BioshipMeatDrops'],'Threat/prey sensing and shared telegraphed swipes. Native drops remain world items; missing meats are added by a drop adapter.'),
('hostile','Native mob targeting','active',['BioshipHostility','BioshipTargetEntity'],'Inject hostile target goals; attack-surface proxy redirects native attacks to the core body.'),
('allies','Owner and ally permissions','active',['BioshipAccess','ShipMembraneAccess'],'Owner/whitelist checks protect allies and refresh legacy membrane permissions.'),
('death','Corpse lineage and decay','active',['BioshipDecay'],'Mark core removal/death, retain saved fragment lineage and rot hull before organs/special blocks.'),
('debug','Read-only diagnostic transport','active',['BioshipNetwork','NavigationDebug','VisionDebug'],'Rate-limited server snapshots of actual perception, route, chosen behavior and outstanding needs.'),
('render','Client rendering','active',['client.BioshipClient','client.AssimilationArmRenderer','client.EquipmentModels','amoeba.client.ComponentTransportRendering','mixin.ComponentTransportRenderMixin'],'Client registration, core/collection arms, supported legs and equipment meshes.'),
('overlay','Sight, path and status overlays','active',['BioshipDiagnostics','client.SightDebugOverlay','client.PathDebugOverlay','client.AppendageDebugOverlay','client.SymbioticTooltip'],'F3+B sight; F3+P native floating route/priority display; goggles status above hotbar.'),
('tools','Player tools and stomach UI','active',['BioshipWrenchItem','BioshipGogglesItem','StomachMenu','client.StomachScreen'],'Conserved manual component moves, wearable inspection and digestive inventory UI.'),
('lab','Packaged development world','active',['NavigationLab','EcosystemLab','AquaticLab','OceanLabTerrain','LabHabitatTerrain','EndHabitatLab','DeveloperTestTool','LabPopulation','LabStarters'],'Normal-world flat/elevation glass courses, world preset and reset command.'),
('platform','Sable and assembly hooks','active',['mixin.SimAssemblyHelperMixin','mixin.DisassemblyPreventionMixin','mixin.ServerSubLevelMixin','mixin.SablePlotUnloadMixin'],'Protected assembly, safe transfer/disassembly, split lineage and Sable-owned plot unloading.'),
('accesshooks','Membrane and key hooks','active',['mixin.BiometricMembraneAccessMixin','mixin.BiometricSubLevelCollisionMixin','mixin.PathDebugKeyMixin'],'Native biometric permission/collision hooks and independent F3+P key handling.'),
('gate','Airship implementation boundary','gate',['disabled.wip.AirshipPrototype'],'available() returns false. Saved stage data does not activate hull construction or flight.'),
('layout','Preserved hull and room geometry','dormant',['disabled.wip.DinghyGrowth','disabled.wip.EngineRoomLayout','disabled.wip.EngineRoomSizing','disabled.wip.BioshipRoomOwnership'],'Prototype shell, bay capacity and attachment-to-room attribution; shared constants/geometry are retained.'),
('engine','Preserved engine-room investment','dormant',['disabled.wip.BioshipEngineRoomPlanner','disabled.wip.BioshipEngineRoom','disabled.wip.EngineRoom'],'Room quotes and expansion are disabled. EngineRoom.storage() remains a shared native-sac predicate.'),
('blueprint','Preserved reconstruction prototype','dormant',['disabled.wip.BioshipBlueprint','disabled.wip.BlueprintMenu','disabled.wip.BlueprintPanelBlock','disabled.wip.client.BlueprintScreen'],'Reconstruction jobs and UI are disabled; BioshipBlueprint.physical() remains a shared coordinate helper.'),
('controls','Preserved flight controls','dormant',['disabled.wip.LivingHelmBlock','disabled.wip.LivingThrottleBlock','disabled.wip.NerveControlBlock','disabled.wip.PilotAuthority','disabled.wip.client.LivingControlHandler'],'Control input and throttle packets are rejected; client hold-control registration is disabled.'),
('cockpit','Preserved cockpit models and UI','dormant',['disabled.wip.CaptainSeatBlock','disabled.wip.BuoyancyOrganBlock','disabled.wip.LivingDoorBlock','disabled.wip.AccessMenu','disabled.wip.AccessPanelBlock','disabled.wip.client.AccessScreen'],'Models, registry IDs, shapes and screens retained; no active airship progression or control UI.'),
('legacyarms','Preserved flagella prototype','dormant',['disabled.wip.TentacleBlockEntity','disabled.wip.TentacleBlock','disabled.wip.FlagellumMotion','disabled.wip.client.FlagellumRenderer'],'Physical flagella source/models retained. Mature-owner hunting and flight muscles are inactive; temporary hatchling arms remain active.'),
('rescue','Preserved flight rescue','dormant',['disabled.wip.BioshipRescue','mixin.ReconfigurationHeatMapMixin'],'New flight rescues are unavailable; saved active rescues may close safely and restore retracted tissue.')]

GROUPS += [
('appendages','Leg capabilities and mouth-bound graspers','active',['LocomotionProfile','LocomotionProfiles','BioshipSurfaceContacts','BioshipGraspers','BioshipGrasperEvolution','client.TentacleSuckers','MaterialRenderPass'],'Reloadable movement capabilities, physical surface contacts, per-mouth grasper slots and shared sucker rendering. Legs remain separate and are the future flight lineage.'),
('tissue','Ordinary tissue and reserved armor','active',['BioshipTissue'],'New ordinary tissue uses biomancy:packed_flesh. Legacy living_hull and new living_hull_tier_2 retain armor IDs; embedded hosts preserve their material.'),
('feedback','Trust, poison and damage feedback','active',['BioshipTrustFeedback','BioshipPoisonFeedback','BioshipParticleSurface','TrustParticleBudget','client.TrustParticleFeedback','client.BioshipHurtFlash'],'Shared trust cooldown; continuous poison status; synchronized whole-body hurt overlay. Feedback never owns a navigation goal.'),
('voice','Per-mouth voice and articulation','active',['BioshipLarynx','BioshipVoiceCommands','BioshipVoice','BioshipVoicePalette','client.BioshipVoicePlayer','client.MouthRenderer'],'A larynx is conserved inside one upgraded mouth. Paid voice growth follows functional digestion and urgent survival. A-Z is a pre-speech creature palette, not spoken letter names.')]

def scrub(s):
    # Preserve offsets/newlines while discarding comments and literal contents.
    return re.sub(r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*[\s\S]*?\*/',lambda m:''.join('\n' if c=='\n' else ' ' for c in m.group()),s)

def members(src):
    clean=scrub(src); body=clean.index('{',clean.index('class ') if 'class ' in clean else 0)
    depth=1; start=body+1; out=[]
    for i in range(body+1,len(clean)):
        c=clean[i]
        if depth==1 and c in '{;':
            original=src[start:i].strip(); plain=clean[start:i].strip()
            if plain:
                pretty=re.sub(r'\s+',' ',re.sub(r'@[\w.]+(?:\([^)]*\))?\s*','',plain)).strip()
                line=src.count('\n',0,start)+1
                if pretty and pretty not in ['static'] and not pretty.startswith((')', ',', 'else', 'return ')):
                    if re.search(r'\b(class|interface|enum|record)\s+\w+',plain):kind='nested type'
                    elif '=' not in plain and '(' in plain:kind='method'
                    else:kind='field'
                    if pretty and len(pretty)<1800:
                        signature=pretty
                        if kind=='field' and '=' in pretty:
                            signature=pretty.split('=')[0].strip()
                            names=re.findall(r',\s*([A-Za-z_$][\w$]*)\s*=',pretty)
                            if names:signature+=', '+', '.join(names)
                        out.append({'kind':kind,'signature':signature,'line':line})
            start=i+1
        if c=='{':depth+=1
        elif c=='}':
            depth-=1
            if depth==1:start=i+1
            if depth==0:break
        elif depth==1 and c==';':start=i+1
    return out

ALL={str(p.relative_to(BASE).with_suffix('')).replace('/','.'):p for p in BASE.rglob('*.java')}
assigned=[c for _,_,_,cs,_ in GROUPS for c in cs]
assert len(assigned)==len(set(assigned)), 'Duplicate class assignment'
assert set(assigned)==set(ALL), ('Unmapped',set(ALL)-set(assigned),'Unknown',set(assigned)-set(ALL))
DATA={}
for key,p in ALL.items():
    src=p.read_text(); m=re.search(r'(?:public\s+)?(?:(?:final|abstract)\s+)?(?:class|interface|record|enum)\s+'+re.escape(p.stem)+r'[^\{]*',src)
    DATA[key]={'path':str(p.relative_to(ROOT)),'declaration':re.sub(r'\s+',' ',m.group()).strip() if m else p.stem,'members':members(src),'lines':len(src.splitlines())}

OVERVIEW = '''# Bioship systems and class reference

This is a text flowchart and source-derived class reference, split into readable sections.
Arrows describe scheduling or data flow, not inheritance. Class declarations, fields and methods
are listed beneath each subsystem. Prototype airship code is preserved and disabled.

Generated from the working tree. Source paths and declaration line numbers identify the code.
The member index lists signatures rather than method bodies; nested implementation details remain in source.

## 1. Runtime and decisions

```text
[Core placement + Sable assembly]
                 |
                 v
[LivingCoreBlockEntity.tick]
   |-- chunk loading + aquatic safety
   |-- observe vision + remembered targets
   |-- action tree: BioshipBrain.choose
   |      |-- safety, needs before goals, weighted GoalQueue
   |      `-- crawl / defense / external collection
   |-- body tree: BioshipBodyBehavior.choose (20-tick cadence)
   |      |-- separate weighted GoalQueue + feasibility checks
   |      `-- BioshipAdaptation / BioshipConfiguration
   |             `-- repair, growth, native organ conversion
   |-- shared layout lock: carried organs / core moves / rescue / blueprint
   |-- atomic material account: revalidated debit at execution
   |
   `-- serverTick (once per game tick)
          |-- metabolism every 20 ticks
          |-- vision / health / repair
          `-- physics intent -> Sable body
```

The core remains an orchestrator. Some actuators still call choose() again in the same tick;
this is not yet a single immutable decision snapshot. Body work is selected independently of combat,
but in-flight component/layout transactions serialize conflicting mutations. An affordability quote
is not resource escrow; paid work debits the shared account before mutation.

## 2. Food, materials and growth

```text
[Visible component / dead wreck / dropped item]
                      |
       [Brain: rank current needs and goals]
                      |
       [Crawl route + reach + ComponentTransport]
                      |
       [Assimilation: conserved internal stock]
             |                    |
      keep needed part       lossy recycling
             |                    |
      settle / combine      flesh / fiber / bone
             |                    |
      mouth -> intestine -> stomach
                             |       |
                       local paste   native recipe products
                             |       |
                        metabolism   core reserve -> overflow sacs
                                      |
                         shared account pays adaptations
```

Paid adaptations require funding, reserves and applicable support checks. Digestive recovery
precedes vocal upgrades. Each working mouth has its own stomach and intact gut; each vocal mouth
has its own absorbed larynx capsule. Capsules preserve native configuration and essence data.
Ordinary tissue is Packed Flesh; reserved armor blocks do not enable adult hull progression.

## 3. Navigation, sight and combat

```text
[Core eyes + surface eyes]
             |
[Cones + light + obstruction rays]
             |
[Observed candidates + three-second memory]
             |
[Needs/goals -> selected target]
             |
[GroundRoute: body clearance + elevation]
             |
[Crawl: contacts + mass + leg capacity]
       |                    |
[Support / Righting]    [Defense / prey]
       |                    |
[Physics intent]      [Tentacle strike -> normal loot drops]
```

Open corridors use direct whole-body clearance checks. Obstructed routes use diagonal search and straight-segment simplification; extended surface search follows a failed quick detour. Known kill drops bypass visual memory expiry, not physical reach. Visibility and physical reach are separate checks. F3+B shows sight diagnostics; F3+P shows
navigation and behavior diagnostics. Neither toggle enables ordinary trust, poison, hurt or mouth effects.

## 4. Feedback and client rendering

```text
[Provisioned meal / player mistreatment]
             |
[Trust feedback: one burst per 60 game ticks]
             |
[Network -> client frame budget -> <=16 particles]

[Poison status] -> green status particles until recovery/death
[Actual damage] -> eight-tick whole-body red overlay + short voice

[Native or paid larynx] -> [one mouth's preserved capsule]
                                  |
             [Voice sample + envelope + pitch modulation]
                                  |
             [Mouth-position sound + synchronized jaw motion]

[Accepted food / bite] -> [closing jaw -> swallowing feedback]
```

The A-Z palette consists of distinct bounded creature articulations for future speech. It is
not natural-language speech synthesis. Sounds reuse Biomancy events; no dependency audio is copied.
Client sound envelopes stop samples after their short utterance, with attack/release and mild pitch bends.

## 5. Dormant airship boundary

```text
[AirshipPrototype.available() == false]
             |
             |-- no automatic adult hull / engine-room build
             |-- no flight or piloting
             |-- no blueprint reconstruction or new rescue jobs
             `-- registry IDs, models, source and saved data preserved

Shared helpers retained:
  BioshipBlueprint.physical() -> coordinate conversion
  EngineRoom.storage()       -> native sac classification
  layout constants          -> serialization / shared geometry
```

## Class structure by subsystem
'''

out = [OVERVIEW]
for key, title, status, classes, desc in GROUPS:
    out += [f'\n### {title} [{status}]\n', desc+'\n', '```text\n['+key+']\n  '+ '\n  '.join(classes)+'\n```\n']
    for name in classes:
        data=DATA[name]
        out += [f'\n#### {name}\n', f'Source: `{data["path"]}` ({data["lines"]} lines).\n', '```java\n'+data['declaration']+'\n']
        for member in data['members']:
            out.append(f'  {member["signature"]}; // line {member["line"]}\n')
        out.append('```\n')
text='\n'.join(out)
OUT.mkdir(parents=True, exist_ok=True)
(OUT/'systems-reference.md').write_text(text)
(OUT/'systems-class-index.json').write_text(json.dumps({'groups':GROUPS,'classes':DATA},indent=2)+'\n')

if '--pdf' in __import__('sys').argv:
    import textwrap
    from reportlab.platypus import SimpleDocTemplate, Paragraph, Preformatted, Spacer, PageBreak
    from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
    from reportlab.lib.pagesizes import A4
    from reportlab.lib import colors
    styles=getSampleStyleSheet()
    code=ParagraphStyle('ReadableCode',fontName='Courier',fontSize=8,leading=10,spaceAfter=8)
    doc=SimpleDocTemplate(str(OUT/'systems-reference.pdf'),pagesize=A4,rightMargin=40,leftMargin=40,topMargin=40,bottomMargin=40)
    flow=[]; fenced=False; lines=[]
    def flush():
        if lines:
            wrapped=[]
            for line in lines:
                wrapped.extend(textwrap.wrap(line,98,replace_whitespace=False,drop_whitespace=False,subsequent_indent='    ') or [''])
            # Split code into bounded chunks so long class signatures paginate normally.
            for start in range(0,len(wrapped),35): flow.append(Preformatted('\n'.join(wrapped[start:start+35]), code))
            lines.clear()
    for line in text.splitlines():
        if line.startswith('```'):
            if fenced: flush()
            fenced=not fenced
        elif fenced: lines.append(line)
        elif line.startswith('#'):
            level=len(line)-len(line.lstrip('#'))
            if level==2: flow.append(PageBreak())
            flow.append(Paragraph(html.escape(line.lstrip('# ')),styles['Heading'+str(min(level,3))]))
        elif line.strip(): flow.append(Paragraph(html.escape(line.replace('`','')), styles['BodyText']))
        else: flow.append(Spacer(1,5))
    def footer(canvas, doc):
        canvas.setFont('Helvetica',8); canvas.setFillColor(colors.HexColor('#555555'))
        canvas.drawString(40,23,'Bioship systems - text flowcharts and source class reference')
        canvas.drawRightString(A4[0]-40,23,str(doc.page))
    doc.build(flow,onFirstPage=footer,onLaterPages=footer)
print(f'Indexed {len(DATA)} source classes in {len(GROUPS)} readable subsystems.')
