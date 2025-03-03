document.addEventListener("DOMContentLoaded", function () {
    fetchUniversities();
});

function displayUniversities(universities) {
    const supportContainer = document.getElementById("supportContainer");
    supportContainer.innerHTML = "";
    
    universities.forEach(uni => {
        const albumCol = document.createElement('div');
        albumCol.classList.add('col-12', 'col-sm-4', 'col-md-3', 'col-lg-2', 'single-album-item', 'dynamic');

        const uniElement = document.createElement("div");
        uniElement.classList.add("macchinetta-item");

        uniElement.innerHTML = `
            <div class="album-info">
                <a href="#">
                    <h5>${uni.name}</h5>
                    <button class="macchinetteButton">Apri Macchinette</button>
                </a>
            </div>
        `;

        albumCol.appendChild(uniElement);
        supportContainer.appendChild(albumCol);
    });
}