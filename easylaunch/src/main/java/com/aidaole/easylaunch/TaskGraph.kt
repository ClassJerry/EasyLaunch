package com.aidaole.easylaunch

import android.util.Log

/**
 * 任务图类
 * 用于管理所有任务节点和检测环
 */
internal class TaskGraph {
    companion object {
        private const val TAG = "TaskGraph"
    }
    
    /**
     * 所有任务节点
     */
    private val nodes = mutableMapOf<String, TaskNode>()
    
    /**
     * 是否已经构建了依赖关系
     */
    private var dependenciesBuilt = false
    
    /**
     * 添加任务
     * 只是将任务添加到图中，不立即构建依赖关系
     */
    fun addTask(task: Task) {
        val taskName = task.javaClass.name
        if (nodes.containsKey(taskName)) {
            return
        }
        
        val node = TaskNode(task)
        nodes[taskName] = node
        
        // 添加任务后，标记依赖关系需要重新构建
        dependenciesBuilt = false
    }
    
    /**
     * 构建所有任务的依赖关系
     * 在所有任务都添加完成后调用此方法
     */
    fun buildDependencies() {
        if (dependenciesBuilt) {
            return
        }
        
        // 清除现有的依赖关系
        nodes.values.forEach { node ->
            node.children.clear()
            node.inDegree.set(0)
        }
        
        // 重新构建所有依赖关系
        nodes.values.forEach { node ->
            val task = node.task
            task.dependencies.forEach { dependencyClass ->
                val dependencyName = dependencyClass.name
                val dependencyNode = nodes[dependencyName]
                
                if (dependencyNode == null) {
                    Log.w(TAG, "依赖任务 $dependencyName 未找到，将被忽略。依赖此任务的是: ${task.javaClass.name}")
                } else {
                    // 建立父子关系，重复声明同一条依赖时只计一次入度
                    if (!dependencyNode.children.contains(node)) {
                        dependencyNode.addChild(node)
                        node.inDegree.incrementAndGet()
                    }
                }
            }
        }
        
        // 检测是否有环
        if (hasCycle()) {
            throw IllegalStateException("任务图中存在环，请检查任务依赖关系")
        }
        
        dependenciesBuilt = true
    }
    
    /**
     * 获取所有没有依赖的任务节点（入度为0的节点）
     * 在获取前确保依赖关系已构建
     */
    fun getRootNodes(): List<TaskNode> {
        if (!dependenciesBuilt) {
            buildDependencies()
        }
        return nodes.values.filter { it.inDegree.get() == 0 }
    }
    
    /**
     * 获取所有任务节点
     */
    fun getAllNodes(): List<TaskNode> {
        return nodes.values.toList()
    }
    
    /**
     * 获取所有就绪的任务节点
     * 就绪的任务节点是指所有父节点都已完成的节点
     */
    fun getReadyNodes(): List<TaskNode> {
        if (!dependenciesBuilt) {
            buildDependencies()
        }
        return nodes.values.filter { 
            it.status == TaskStatus.IDLE && it.isReady 
        }
    }
    
    /**
     * 检测是否有环
     * 使用Kahn算法检测
     */
    private fun hasCycle(): Boolean {
        // 使用局部入度副本，避免在检测过程中污染节点上供调度使用的真实入度
        val remainingInDegree = HashMap<TaskNode, Int>(nodes.size)
        val queue = ArrayDeque<TaskNode>()

        // 初始化：所有入度为0的节点入队
        nodes.values.forEach { node ->
            val degree = node.inDegree.get()
            remainingInDegree[node] = degree
            if (degree == 0) {
                queue.addLast(node)
            }
        }

        var processedCount = 0

        // 逐个出队并“删除”其出边：子节点入度减1，减到0则入队
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            processedCount++

            node.children.forEach { child ->
                val degree = (remainingInDegree[child] ?: 0) - 1
                remainingInDegree[child] = degree
                if (degree == 0) {
                    queue.addLast(child)
                }
            }
        }

        // 仍有节点未被处理，说明这些节点的入度无法归零，即存在环
        return processedCount < nodes.size
    }
    
    /**
     * 重置所有任务状态
     */
    fun reset() {
        nodes.values.forEach { it.status = TaskStatus.IDLE }
        dependenciesBuilt = false
    }
    
    /**
     * 清空任务图
     */
    fun clear() {
        nodes.clear()
        dependenciesBuilt = false
    }
} 