Behaviour.specify("BUTTON.advanced-button", 'advanced', 0, function(e) {
        e.addEventListener('click', function(e) {
            var link = $(e.target).up(".advancedLink");
            var tr;
            link.style.display = "none"; // hide the button

            var container = link.next("table.advancedBody");
            if (container) {
                container = container.down(); // TABLE -> TBODY
                tr = link.up("TR");
            } else {
                container = link.next("div.advancedBody");
                tr = link.up(".tr");
            }

            // move the contents of the advanced portion into the main table
            var nameRef = tr.getAttribute("nameref");
            while (container.lastElementChild != null) {
                var row = container.lastElementChild;
                if(nameRef!=null && row.getAttribute("nameref")==null)
                    row.setAttribute("nameref",nameRef); // to handle inner rowSets, don't override existing values
                $(row).setOpacity(0);

                tr.parentNode.insertBefore(row, $(tr).next());

                new YAHOO.util.Anim(row, {
                    opacity: { to:1 }
                }, 0.2, YAHOO.util.Easing.easeIn).animate();

            }
            layoutUpdateCallback.call();
        });
        e = null; // avoid memory leak
});
