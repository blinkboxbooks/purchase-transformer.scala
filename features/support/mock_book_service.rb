require 'sinatra'
require 'json'
require 'cgi'

# Special ISBN:
# used to signal that a book with an unknown contributor should be returned.
UNKNOWN_CONTRIBUTOR_ID ||= "404404"
# used to signal that a book without any author (contributor) should be returned.
NO_AUTHOR_ID ||= "505505"

# This class specifies a web service that responds to book, contributor and publisher
# requests, returning simple responses.
class MockBookService < Sinatra::Base

  get '/service/catalogue/books', provides: :json do
    all_params = CGI::parse(request.query_string)
    ids = all_params["id"].map { |id| id.to_i }
    halt 404, "Requested ID 404 not found" if ids.include? 404
    halt 500, "Server error 500" if ids.include? 500
    books_response(ids).to_json
  end


  def books_response(ids)
    book_responses = ids.map { |id| book_response(id) }
    {
        type: "urn:blinkboxbooks:schema:list",
        numberOfResults: ids.size,
        offset: 0,
        count: 50,
        items: book_responses
    }
  end

  def book_response(id)
    contributor_id = (id.to_s == UNKNOWN_CONTRIBUTOR_ID) ? "404" : "contributor-guid"
    response = {
        type: "urn:blinkboxbooks:schema:book",
        guid: "urn:blinkboxbooks:id:book:#{id}",
        id: id.to_s,
        title: "Title-#{id}",
        publicationDate: "2009-12-30",
        sampleEligible: true,
        images: [
            {
                rel: "urn:blinkboxbooks:image:cover",
                src: "http://internal-media.mobcastdev.com/9780/007/324/354/763ef268eb71d02b6e5d9a80afba7899.png"
            }
        ],
        links: []
    }

    response[:links].push({
                              rel: "urn:blinkboxbooks:schema:contributor",
                              href: "/service/catalogue/contributors/#{contributor_id}",
                              targetGuid: "urn:blinkboxbooks:id:contributor:#{contributor_id}",
                              title: "Author-#{id}"
                          }) unless (id.to_s == NO_AUTHOR_ID)

    response[:links].push({
                              rel: "urn:blinkboxbooks:schema:synopsis",
                              href: "/service/catalogue/books/#{id}/synopsis",
                              targetGuid: "urn:blinkboxbooks:id:synopsis:#{id}",
                              title: "Synopsis"
                          })

    response[:links].push({
                              rel: "urn:blinkboxbooks:schema:publisher",
                              href: "/service/catalogue/publishers/479",
                              targetGuid: "urn:blinkboxbooks:id:publisher:479",
                              title: "Publisher #{id}"
                          })

    response[:links].push({
                              rel: "urn:blinkboxbooks:schema:bookpricelist",
                              href: "/service/catalogue/prices?isbn=#{id}",
                              title: "Price"
                          })

    response[:links].push({
                              rel: "urn:blinkboxbooks:schema:samplemedia",
                              href: "http://internal-media.mobcastdev.com/9780/007/324/354/727b8fd489ce67e7b5d195c89179cb44.sample.epub",
                              title: "Sample"
                          })

    response
  end

end