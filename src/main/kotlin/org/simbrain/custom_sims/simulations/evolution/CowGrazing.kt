package org.simbrain.custom_sims.simulations

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.launch
import org.simbrain.custom_sims.createControlPanel
import org.simbrain.custom_sims.newSim
import org.simbrain.network.NetworkComponent
import org.simbrain.network.core.Network
import org.simbrain.network.core.Synapse
import org.simbrain.network.groups.NeuronCollection
import org.simbrain.network.util.BiasedScalarData
import org.simbrain.util.*
import org.simbrain.util.decayfunctions.StepDecayFunction
import org.simbrain.util.geneticalgorithm2.*
import org.simbrain.util.piccolo.*
import org.simbrain.util.widgets.ProgressWindow
import org.simbrain.workspace.Workspace
import org.simbrain.workspace.serialization.WorkspaceSerializer
import org.simbrain.world.odorworld.OdorWorldComponent
import org.simbrain.world.odorworld.entities.EntityType
import org.simbrain.world.odorworld.entities.OdorWorldEntity
import org.simbrain.world.odorworld.sensors.TileSensor
import java.awt.Dimension
import java.io.FileInputStream
import java.io.IOException
import kotlin.random.Random

val grazingCows = newSim {

    var maxGenerations = 50
    var iterationsPerRun = 2000
    var populationSize = 100
    var eliminationRatio = .5

    class CowGenotype(seed: Long = Random.nextLong()) : Genotype2 {
        override val random: Random = Random(seed)
        var inputChromosome = chromosome2(1) {
            repeat(3) {
                add(nodeGene2 { isClamped = true })
            }
            // Won't get coupled to. Serves as an initial "drive" neuron
            add(nodeGene2 { isClamped = true; forceSetActivation(1.0) })
        }
        var hiddenChromosome = chromosome2(2) { add(nodeGene2()) }
        var outputChromosome = chromosome2(3) { add(nodeGene2 { upperBound = 10.0; lowerBound = -10.0 }) }
        var connectionChromosome = chromosome2(1) {
            repeat(3) {
                add(connectionGene2(inputChromosome.sampleOne(), hiddenChromosome.sampleOne()))
                add(connectionGene2(hiddenChromosome.sampleOne(), outputChromosome.sampleOne()))
            }
            // Force an initial "drive"
            add(connectionGene2(inputChromosome[3], hiddenChromosome.sampleOne()))
        }

        inner class Phenotype(
            val inputs: NeuronCollection,
            val hiddens: NeuronCollection,
            val outputs: NeuronCollection,
            val connections: List<Synapse>
        )

        suspend fun expressWith(network: Network): Phenotype {
            return Phenotype(
                NeuronCollection(network, network.express(inputChromosome)).also {
                    network.addNetworkModel(it); it.label = "input"
                },
                NeuronCollection(network, network.express(hiddenChromosome)).also {
                    network.addNetworkModel(it); it.label = "hidden"
                },
                NeuronCollection(network, network.express(outputChromosome)).also {
                    network.addNetworkModel(it); it.label = "output"
                },
                network.express(connectionChromosome)
            )
        }

        fun copy() = CowGenotype(random.nextLong()).apply {
            val current = this@CowGenotype
            val new = this@apply
            new.inputChromosome = current.inputChromosome.copy()
            new.hiddenChromosome = current.hiddenChromosome.copy()
            new.outputChromosome = current.outputChromosome.copy()
            new.connectionChromosome = current.connectionChromosome.copy()
        }

        fun mutate() {
            hiddenChromosome.forEach {
                it.mutate {
                    with(dataHolder as BiasedScalarData) {
                        bias += random.nextDouble(-1.0, 1.0)
                    }
                }
            }
            connectionChromosome.forEach {
                it.mutate {
                    strength += random.nextDouble(-1.0, 1.0)
                }
            }

            val existingPairs = connectionChromosome.map { it.source to it.target }.toSet()
            val availableConnections =
                ((inputChromosome + hiddenChromosome + outputChromosome) cartesianProduct (hiddenChromosome + outputChromosome)) - existingPairs
            if (random.nextDouble() < 0.25 && availableConnections.isNotEmpty()) {
                val (source, target) = availableConnections.sampleOne(random)
                connectionChromosome.add(connectionGene2(source, target) { strength = random.nextDouble(-1.0, 1.0) })
            }

            // Make hidden layer larger
            if (random.nextDouble() < 0.1) {
                hiddenChromosome.add(nodeGene2())
            }
        }
    }

    // What to do when a cow finds flower
    fun addFindFlowerAction(workspace: Workspace, entity: OdorWorldEntity, fitnessLambda: (Double) -> Unit = {}) {
        val world = entity.world
        workspace.addUpdateAction("${entity.name} found a flower") {
            with(world.tileMap) {
                val flowerLayer = this.getLayer("Flower Layer")

                fun randomTileCoordinate() = Random.nextGridCoordinate()
                (entity.getSensor("centralFlowerSensor") as TileSensor).let { sensor ->
                    // Flowers found
                    if (sensor.currentValue > .5) {

                        // Erase that tile
                        entity.location.asPixelCoordinate().toGridCoordinate().int.let { (x, y) ->
                            flowerLayer.setTile(x, y, 0)
                        }

                        // Add a new flower somewhere else
                        randomTileCoordinate().int.let { (x, y) ->
                            world.tileMap.setTile(x, y, "DaisyCenter", flowerLayer)
                        }

                        // Update fitness
                        fitnessLambda(1.0)
                    }
                }
            }
        }

    }


    class CowSim(
        val cowGenotypes: List<CowGenotype> = List(2) { CowGenotype() },
        val workspace: Workspace = Workspace()
    ) : EvoSim {

        val random = Random(cowGenotypes.first().random.nextInt())

        val cowFitnesses = mutableMapOf<CowGenotype.Phenotype, Double>()

        private val _cowPhenotypes = CompletableDeferred<List<CowGenotype.Phenotype>>()
        val cowPhenotypes: Deferred<List<CowGenotype.Phenotype>> get() = _cowPhenotypes

        fun randomTileCoordinate() = with(odorWorld.tileMap) { random.nextGridCoordinate() }
        private val lakeSize
            get() = random.nextInt(2, 8)

        val odorWorld = OdorWorldComponent("Odor World 1").also {
            workspace.addWorkspaceComponent(it)
        }.world.apply {
            loadTileMap("empty.tmx")
            with(tileMap) {
                updateMapSize(25, 25)
                fill("Grass1")
            }
        }
        val flowerLayer = odorWorld.tileMap.run {
            addLayer(createTileMapLayer("Flower Layer"))
        }

        val networks = List(cowGenotypes.size) { index ->
            NetworkComponent("Network ${index + 1}").also { workspace.addWorkspaceComponent(it, true) }.network
        }
        val entities = List(cowGenotypes.size) { i ->
            OdorWorldEntity(odorWorld, EntityType.COW).also {
                odorWorld.addEntity(it)
                it.location = point((i + 1) * 100, (i + 1) * 100)
            }
        }

        // Water sensors that can guide the cow
        val sensors = entities.map { entity ->
            List(3) { index ->
                TileSensor("flower", radius = 60.0, angle = (index * 120.0)).apply {
                    decayFunction.dispersion = 250.0
                }.also { entity.addSensor(it) }
            }
        }

        // Central water sensor to determine when water is actually found.
        val centerFlowerSensors = entities.associateWith { entity ->
            TileSensor("flower", radius = 0.0).apply {
                label = "centralFlowerSensor"
                decayFunction = StepDecayFunction()
                decayFunction.dispersion = 30.0
            }.also { entity.addSensor(it) }
        }
        val effectors = entities.map { entity ->
            entity.addDefaultEffectors()
            entity.effectors
        }

        init {
            addFlowers()
            workspace.launch {
                (cowPhenotypes.await() zip entities).forEach { (phenotype, entity) ->
                    addUpdateActions(phenotype, entity)
                }
            }
        }

        fun addFlowers(numFlowers: Int = 5) {
            odorWorld.tileMap.clear(flowerLayer)
            List(numFlowers) { randomTileCoordinate().int }.forEach {
                odorWorld.tileMap.setTile(it.x, it.y, "DaisyCenter", flowerLayer)
            }
        }

        fun addUpdateActions(cow: CowGenotype.Phenotype, entity: OdorWorldEntity) {

            fun addFitness(fitnessDelta: Double) {
                cowFitnesses[cow] = (cowFitnesses[cow] ?: 0.0) + fitnessDelta
            }
            addFitness(0.0) // To initialize fitness

            addFindFlowerAction(workspace, entity) { addFitness(1.0) }
        }

        override suspend fun build() {
            if (!_cowPhenotypes.isCompleted) {
                // Express the genotypes
                _cowPhenotypes.complete(
                    cowGenotypes.zip(networks).map { (genotype, network) -> genotype.expressWith(network) })
                // Make couplings
                with(workspace.couplingManager) {
                    val cows = _cowPhenotypes.await()
                    (0..cows.lastIndex).map { i ->
                        val cow = cows[i]
                        val sensor = sensors[i]
                        val effector = effectors[i]
                        sensor couple cow.inputs.neuronList
                        cow.outputs.neuronList couple effector
                    }
                }
            }
        }

        override fun mutate() {
            cowGenotypes.forEach { it.mutate() }
        }

        override fun visualize(workspace: Workspace) = CowSim(cowGenotypes.map { it.copy() }, workspace)

        override fun copy() = CowSim(cowGenotypes.map { it.copy() }, Workspace())

        override suspend fun eval(): Double {
            build()
            workspace.iterateSuspend(iterationsPerRun)
            // Determine a fitness for the sim based on the fitness of each cow
            return cowFitnesses.values.minOrNull() ?: 0.0
        }
    }

    fun runSim() {
        workspace.launch {
            val progressWindow = ProgressWindow(maxGenerations, "10th Percentile Fitness:").apply {
                minimumSize = Dimension(300, 100)
                setLocationRelativeTo(null)
            }
            val cowSims = evaluator2(
                populatingFunction = { CowSim() },
                populationSize = populationSize,
                eliminationRatio = eliminationRatio,
                stoppingFunction = {
                    nthPercentileFitness(10) > 400 || generation > maxGenerations
                },
                peek = {
                    listOf(0, 10, 25, 50, 75, 90, 100).joinToString(" ") {
                        "$it: ${nthPercentileFitness(it).format(3)}"
                    }.also {
                        println("[$generation] $it")
                        progressWindow.text = "10th Percentile Fitness: ${nthPercentileFitness(10).format(3)}"
                        progressWindow.value = generation
                    }
                }
            )
            cowSims.take(1).forEach {
                with(it.visualize(workspace) as CowSim) {
                    build()
                    withGui {
                        workspace.componentList.filterIsInstance<OdorWorldComponent>().first().apply {
                            place(this, 280, 10, 476, 432)
                        }
                        workspace.componentList.filterIsInstance<NetworkComponent>().forEachIndexed { i, net ->
                            place(net, 768, 10 + i * 282, 326, 282)
                        }
                    }
                    cowPhenotypes.await().forEach {
                        it.inputs.location = point(0, 150)
                        it.hiddens.location = point(0, 60)
                        it.outputs.location = point(0, -25)
                    }
                }
            }
            progressWindow.close()
        }
    }

    withGui {
        workspace.clearWorkspace()
        createControlPanel("Control Panel", 5, 10) {

            val maxGenTf = addTextField("Max Generations", "" + maxGenerations)
            val iterationsPerRunTf = addTextField("Num iterations per generation", "" + iterationsPerRun)
            val populationSizeTf = addTextField("Population size", "" + populationSize)
            val eliminationRatioTf = addTextField("Elimination ratio", "" + eliminationRatio)

            addButton("Evolve") {
                maxGenerations = maxGenTf.text.toInt()
                iterationsPerRun = iterationsPerRunTf.text.toInt()
                populationSize = populationSizeTf.text.toInt()
                eliminationRatio = eliminationRatioTf.text.toDouble()
                runSim()
            }

            addButton("Load file") {
                workspace.launch {
                    val simulationChooser = SFileChooser(workspace.currentDirectory, "Zip Archive", "zip")
                    val simFile = simulationChooser.showOpenDialog()
                    val serializer = WorkspaceSerializer(workspace)
                    try {
                        if (simFile != null) {
                            workspace.removeAllComponents()
                            workspace.updater.updateManager.reset()
                            serializer.deserialize(FileInputStream(simFile))
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }

                    val world = workspace.componentList.filterIsInstance<OdorWorldComponent>().first().world
                    world.entityList.forEach { addFindFlowerAction(workspace, it) }

                }
            }
        }
    }


}