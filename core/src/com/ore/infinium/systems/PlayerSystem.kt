/**
MIT License

Copyright (c) 2016 Shaun Reich <sreich02@gmail.com>

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
 */

package com.ore.infinium.systems

import com.artemis.Aspect
import com.artemis.ComponentMapper
import com.artemis.annotations.Wire
import com.artemis.systems.IteratingSystem
import com.badlogic.gdx.math.Vector2
import com.ore.infinium.OreTimer
import com.ore.infinium.OreWorld
import com.ore.infinium.components.*
import com.ore.infinium.systems.server.ServerNetworkSystem
import com.ore.infinium.util.getNullable

@Wire(failOnNull = false)
class PlayerSystem(private val m_world: OreWorld) : IteratingSystem(Aspect.one(PlayerComponent::class.java)) {

    private lateinit var playerMapper: ComponentMapper<PlayerComponent>
    private lateinit var spriteMapper: ComponentMapper<SpriteComponent>
    private lateinit var controlMapper: ComponentMapper<ControllableComponent>
    private lateinit var itemMapper: ComponentMapper<ItemComponent>
    private lateinit var velocityMapper: ComponentMapper<VelocityComponent>
    private lateinit var jumpMapper: ComponentMapper<JumpComponent>

    private lateinit var m_serverNetworkSystem: ServerNetworkSystem

    private val chunkTimer = OreTimer()

    override fun inserted(entityId: Int) {
        super.inserted(entityId)

        //client does nothing as of yet, with this
        if (m_world.worldInstanceType != OreWorld.WorldInstanceType.Server) {
            return
        }

        //initial spawn, send region
        calculateLoadedViewport(entityId)
        sendPlayerBlockRegion(entityId)
    }

    override fun removed(entityId: Int) {
        super.removed(entityId)

    }

    override fun process(entityId: Int) {
        if (m_world.worldInstanceType != OreWorld.WorldInstanceType.Server) {
            return
        }

        //clients, for now, do their own collision stuff. mostly.
        //FIXME: clients should simulate their own player's collision with everything and tell the server its
        // position so it can broadcast.
        // but nothing else.
        //server will simulate everything else(except players), and broadcast positions

        //should never ever, ever happen.
        assert(spriteMapper.has(entityId) && playerMapper.has(entityId))

        val spriteComponent = spriteMapper.getNullable(entityId)!!
        val playerComponent = playerMapper.get(entityId)

        val viewportRect = playerComponent.loadedViewport.rect
        val x = spriteComponent.sprite.x
        val y = spriteComponent.sprite.y

        //fixme fixme, we'll fix this when we get to chunking and come up with a proper solution
        if (chunkTimer.milliseconds() > 600) {
            calculateLoadedViewport(entityId)
            chunkTimer.reset()
        }
    }

    private fun calculateLoadedViewport(playerEntity: Int) {
        val playerComponent = playerMapper.get(playerEntity)
        val spriteComponent = spriteMapper.get(playerEntity)

        val loadedViewport = playerComponent.loadedViewport

        val center = Vector2(spriteComponent.sprite.x, spriteComponent.sprite.y)
        loadedViewport.centerOn(center)

        m_serverNetworkSystem.sendPlayerLoadedViewportMoved(playerEntity)

        //todo send only partials depending on direction they're traveling(distance from origin).
        sendPlayerBlockRegion(playerEntity)
    }

    private fun sendPlayerBlockRegion(playerEntity: Int) {
        val playerComponent = playerMapper.get(playerEntity)
        val loadedViewport = playerComponent.loadedViewport

        val region = loadedViewport.blockRegionInViewport()

        m_serverNetworkSystem.sendPlayerBlockRegion(playerEntity, region.x, region.y, region.width,
                                                    region.height)
    }

}
