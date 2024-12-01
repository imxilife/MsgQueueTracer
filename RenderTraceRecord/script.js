document.addEventListener('DOMContentLoaded', () => {
    const timeline = document.getElementById('timeline');
    const detailsBox = document.getElementById('detailsBox');
    const fileInput = document.getElementById('fileInput');
    const fileName = document.getElementById('fileName');
    const arrow = document.getElementById('arrow');
    let selectedBox = null;

    function displayData(data) {
        timeline.innerHTML = ''; // 清空现有内容
        detailsBox.innerHTML = ''; // 清空详情内容
        arrow.style.display = 'none'; // 隐藏箭头

        data.forEach(item => {
            if (item) {
                const box = document.createElement('div');
                box.classList.add('box');
                box.setAttribute('data-content', JSON.stringify(item));

                // 判断 totalTime 的值，如果为0则取 _wallTime 的值
                const time = item.totalTime !== 0 ? item.totalTime : item._wallTime;

                box.innerHTML = `
                    <div class="number">${item.sequence}</div>
                    <div class="content">耗时 ${time}ms</div>
                    <div class="count">count: ${item.count}</div>
                `;
                timeline.appendChild(box);

                // 添加点击事件
                box.addEventListener('click', () => {
                    // 取消之前选择的高亮
                    document.querySelectorAll('.timeline .box').forEach(b => b.classList.remove('selected'));

                    // 当前 box 高亮
                    box.classList.add('selected');
                    selectedBox = box;

                    // 显示箭头并定位到选中的 box 上方
                    positionArrow();

                    let detailsHtml = `<div class="box"><b>消息详情</b><br>`;
                    detailsHtml += `消息编号: ${item.sequence}<br>`;
                    detailsHtml += `总耗时: ${item.totalTime}ms<br>`;
                    detailsHtml += `等待时间: ${item._waitTime}ms<br>`;
                    detailsHtml += `消息数量: ${item.count}<br>`;
                    detailsHtml += `待处理消息数量: ${item.pendingMessageCount}<br>`;
                    detailsHtml += `目标时间戳: ${item.targetTS}<br>`;
                    detailsHtml += `消息耗时: ${item._wallTime}<br>`;
                    detailsHtml += `信息: ${item.info}<br>`;
                    detailsHtml += `</div>`;

                    detailsBox.innerHTML = detailsHtml;

                    // 确保选中的 box 在可视范围内
                    box.scrollIntoView({ behavior: "smooth", inline: "center" });
                });
            }
        });
    }

    // function positionArrow() {
    //     if (selectedBox) {
    //         const boxRect = selectedBox.getBoundingClientRect();
    //         const containerRect = document.querySelector('.container').getBoundingClientRect();
    
    //         const scrollLeft = document.querySelector('.timeline-container').scrollLeft; // 获取水平滚动条的位置

    //         arrow.style.display = 'block';
    //         const arrowHeight = 10; // 三角形的高度
    //         const lineHeight = 20; // 直线的高度
    //         const spacing = 5; // 箭头底部到 box 顶部的间距
    
    //         arrow.style.left = `${boxRect.left - containerRect.left + boxRect.width / 2 - 10}px`; // 箭头宽度为 20px
    //         arrow.style.top = `${boxRect.top - containerRect.top - arrowHeight - lineHeight - spacing}px`;
    //     }
    // }

    //document.querySelector('.timeline-container').addEventListener('scroll', positionArrow);

    function positionArrow() {
        if (selectedBox) {
            const boxRect = selectedBox.getBoundingClientRect();
            const container = document.querySelector('.timeline-container');
            const containerRect = container.getBoundingClientRect();
            const scrollLeft = container.scrollLeft; // 获取水平滚动条的位置
    
            const arrowHeight = 10; // 三角形的高度
            const lineHeight = 20; // 直线的高度
            const spacing = 5; // 箭头底部到 box 顶部的间距
    
            //const arrowLeft = boxRect.left - containerRect.left + boxRect.width / 2 - 10 + scrollLeft;
            //const arrowTop = boxRect.top - containerRect.top - arrowHeight - lineHeight - spacing;
            const arrowLeft = -100;
            const arrowTop = 130;
    
            // 检查 box 是否在可视区域内
            if (boxRect.right > containerRect.left && boxRect.left < containerRect.right) {
                arrow.style.display = 'block';
                arrow.style.left = `${Math.max(containerRect.left, boxRect.left) - containerRect.left + boxRect.width / 2 - 10}px`;
                arrow.style.top = `${arrowTop}px`;
    
                // 计算可见部分
                const visibleLeft = Math.max(containerRect.left, boxRect.left);
                const visibleRight = Math.min(containerRect.right, boxRect.right);
                const visibleWidth = visibleRight - visibleLeft;
    
                // 使用 clip 属性来控制箭头的可见部分
                arrow.style.clip = `rect(0px, ${visibleWidth}px, ${arrowHeight + lineHeight + spacing}px, 0px)`;
            } else {
                arrow.style.display = 'none';
            }
        }
    }

    // 读取文件内容
    fileInput.addEventListener('change', (event) => {
        const file = event.target.files[0];
        if (file) {
            fileName.textContent = file.name;
            const reader = new FileReader();
            reader.onload = function(event) {
                const content = event.target.result;
                try {
                    const data = JSON.parse(content);
                    displayData(data);
                } catch (error) {
                    alert('文件内容不是有效的 JSON 格式');
                }
            };
            reader.readAsText(file);
        } else {
            fileName.textContent = '未选择任何文件';
        }
    });

    // 确保在窗口大小改变时箭头位置正确
    window.addEventListener('resize', positionArrow);

    // 确保在水平滚动条拖动时箭头位置正确
    document.querySelector('.timeline-container').addEventListener('scroll', positionArrow);
});


document.getElementById('txtFileInput').addEventListener('change', function(event) {
    const file = event.target.files[0];
    if (!file) {
        return;
    }

    const reader = new FileReader();
    reader.onload = function(e) {
        const content = e.target.result;
        const parsedContent = parseTxtContent(content);
        document.getElementById('textContent').innerHTML = parsedContent;
    };
    reader.readAsText(file);
    document.getElementById('txtFileName').textContent = file.name;
});

function escapeHtml(unsafe) {
    return unsafe
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#039;");
}

function parseTxtContent(content) {
    return content.split(/\r?\n/).map(line => {
        // 转义特殊字符
        const escapedLine = escapeHtml(line);
        // 保留空行并显示
        if (escapedLine.trim() === '') {
            return '<div>&nbsp;</div>';
        }
        // 保留并显示所有其他内容
        return `<div>${escapedLine}</div>`;
    }).join('');
}